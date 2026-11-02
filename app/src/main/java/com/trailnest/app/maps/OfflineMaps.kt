package com.trailnest.app.maps

import android.content.Context
import com.trailnest.app.domain.GeoPoint
import com.trailnest.app.domain.OpenFreeMapProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*
import kotlin.math.*

enum class MapPackState { MISSING, PARTIAL, DOWNLOADING, COMPLETE, FAILED, LIMIT }
data class MapPack(val state: MapPackState, val completed: Long = 0, val required: Long = 0, val bytesSaved: Long = 0, val tilesSaved: Long = 0)

/** One explicit foreground download at a time; the SDK's durable database owns
 * tiles, styles, glyphs and sprites. No partial area is labelled available. */
class OfflineMaps private constructor(context: Context) {
    private val manager = OfflineManager.getInstance(context.applicationContext)
    private val regions = mutableMapOf<String, OfflineRegion>()
    private val mutablePacks = MutableStateFlow<Map<String, MapPack>>(emptyMap())
    val packs = mutablePacks.asStateFlow()
    private var active: String? = null
    private var generation = 0L
    private var loading = true
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    init {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions.orEmpty().forEach { region ->
                    val key = region.metadata.toString(Charsets.UTF_8)
                    if (key.matches(Regex("TNMAP2:[a-f0-9]{64}"))) {
                        regions[key] = region
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                if (status != null && regions[key] === region) update(key, status, active == key)
                            }
                            override fun onError(error: String?) { set(key, MapPack(MapPackState.FAILED)) }
                        })
                    }
                }
                loading = false
                mutableReady.value = true
            }
            override fun onError(error: String) { loading = false; mutableReady.value = true }
        })
    }
    private fun set(key: String, pack: MapPack) { mutablePacks.value = mutablePacks.value + (key to pack) }
    private fun update(key: String, status: OfflineRegionStatus, downloading: Boolean) {
        val oversized = status.completedResourceSize > 50_000_000 || status.completedTileCount > 3000
        val complete = status.isRequiredResourceCountPrecise && status.isComplete && !oversized
        set(key, MapPack(when { oversized -> MapPackState.LIMIT; complete -> MapPackState.COMPLETE; downloading -> MapPackState.DOWNLOADING; else -> MapPackState.PARTIAL }, status.completedResourceCount, status.requiredResourceCount, status.completedResourceSize, status.completedTileCount))
        if (complete || oversized) pause(key)
    }
    fun download(key: String, points: List<GeoPoint>) {
        if (loading || active != null) return
        val bounds = bounds(points)
        if (bounds == null || MapNetwork.beginDownload() == null) { set(key, MapPack(MapPackState.LIMIT)); return }
        active = key; set(key, MapPack(MapPackState.DOWNLOADING))
        val operation = ++generation
        fun start(region: OfflineRegion) {
            regions[key] = region
            if (active != key || operation != generation) return
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    if (active == key && operation == generation) update(key, status, true)
                }
                override fun onError(error: OfflineRegionError) {
                    if (active != key || operation != generation) return
                    set(key, MapPack(if (MapNetwork.budget?.stopped == true) MapPackState.LIMIT else MapPackState.FAILED)); pause(key)
                }
                override fun mapboxTileCountLimitExceeded(limit: Long) { if (active == key && operation == generation) { set(key, MapPack(MapPackState.LIMIT)); pause(key) } }
            })
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        }
        val existing = regions[key]
        if (existing != null) start(existing)
        // MapView rasterizes CJK ideographs from installed Android fonts. Fetching
        // all remote ideograph ranges can exhaust a small area's byte allowance.
        else manager.createOfflineRegion(OfflineTilePyramidRegionDefinition(OpenFreeMapProvider.STYLE_URL, bounds, 0.0, 14.0, 1f, false), key.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                if (active == key && operation == generation) start(offlineRegion)
                else offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = Unit
                    override fun onError(error: String) = Unit
                })
            }
            override fun onError(error: String) { if (active == key && operation == generation) { active = null; set(key, MapPack(MapPackState.FAILED)) } }
        })
    }
    fun pause(key: String) {
        regions[key]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        if (active == key) {
            generation++
            active = null
            if (mutablePacks.value[key]?.state == MapPackState.DOWNLOADING) set(key, MapPack(MapPackState.PARTIAL))
        }
    }
    fun delete(key: String, onComplete: (Boolean) -> Unit = {}) {
        pause(key)
        val region = regions[key] ?: run { mutablePacks.value = mutablePacks.value - key; onComplete(true); return }
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { regions.remove(key); mutablePacks.value = mutablePacks.value - key; onComplete(true) }
            override fun onError(error: String) { set(key, MapPack(MapPackState.FAILED)); onComplete(false) }
        })
    }
    companion object {
        @Volatile private var instance: OfflineMaps? = null
        fun get(context: Context): OfflineMaps = instance ?: synchronized(this) {
            MapNetwork.initialise(context)
            instance ?: OfflineMaps(context).also { instance = it }
        }
        fun bounds(points: List<GeoPoint>): LatLngBounds? {
            if (points.size < 2) return null
            val midLat = (points.minOf { it.lat } + points.maxOf { it.lat }) / 2
            val midLon = (points.minOf { it.lon } + points.maxOf { it.lon }) / 2
            // Include the initial camera's 48dp padding in its 320dp view and
            // landscape viewports up to 4:1. A tight route box leaves visible
            // neighbouring tiles absent even when its own region is complete.
            val latSpan = (points.maxOf { it.lat } - points.minOf { it.lat } + .004) * 1.6
            val lonSpan = max((points.maxOf { it.lon } - points.minOf { it.lon } + .004) * 1.6, latSpan * 4 / cos(Math.toRadians(midLat)).coerceAtLeast(.08))
            val south = (midLat - latSpan / 2).coerceAtLeast(-85.0)
            val north = (midLat + latSpan / 2).coerceAtMost(85.0)
            val west = (midLon - lonSpan / 2).coerceAtLeast(-180.0)
            val east = (midLon + lonSpan / 2).coerceAtMost(180.0)
            if (south >= north || west >= east || east - west > 5) return null
            // Include both current style sources, every zoom and a boundary tile
            // margin. The network budget remains authoritative if styles change.
            var count = 0L
            for (z in 0..14) {
                val n = 2.0.pow(z)
                fun x(lon: Double) = floor((lon + 180) / 360 * n).toLong()
                fun y(lat: Double): Long { val r = Math.toRadians(lat); return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n).toLong() }
                count += (x(east) - x(west) + 2) * (y(south) - y(north) + 2) * 2
                if (count > 2000) return null
            }
            return LatLngBounds.Builder().include(LatLng(south, west)).include(LatLng(north, east)).build()
        }
    }
}
