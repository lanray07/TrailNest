package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.core.view.doOnLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailnest.app.domain.GeoPoint
import com.trailnest.app.domain.OpenFreeMapProvider
import com.trailnest.app.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.FeatureCollection

@Composable fun MapPanel(digest: String, points: List<GeoPoint>, premiumAvailable: () -> Boolean, position: GeoPoint? = null, onMapVisibility: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val uri = LocalUriHandler.current
    val offline = remember { OfflineMaps.get(context) }
    val packs by offline.packs.collectAsStateWithLifecycle()
    val ready by offline.ready.collectAsStateWithLifecycle()
    val key = "TNMAP2:$digest"
    val pack = packs[key]
    var show by rememberSaveable(digest) { mutableStateOf(false) }
    var mapError by remember { mutableStateOf(false) }
    LaunchedEffect(show) { onMapVisibility(show) }
    TrailPanel(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.map_title), true)
        TrailText(stringResource(R.string.map_privacy))
        val numbers = java.text.NumberFormat.getIntegerInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
        TrailText(stringResource(R.string.map_budget, numbers.format(40), numbers.format(2800)), style = MaterialTheme.typography.bodySmall)
        if (!show) Button(onClick = { mapError = false; show = true }) { TrailText(stringResource(R.string.map_load)) }
        if (show) {
            NativeRouteMap(points, position) { mapError = true; show = false }
            TrailText(stringResource(R.string.map_legend), style = MaterialTheme.typography.bodySmall)
        }
        if (mapError) TrailText(stringResource(R.string.map_failed))
        TrailText(stringResource(when (pack?.state) {
            MapPackState.COMPLETE -> R.string.map_complete
            MapPackState.DOWNLOADING -> R.string.map_downloading
            MapPackState.FAILED -> R.string.map_failed
            MapPackState.LIMIT -> R.string.map_limit
            else -> R.string.map_partial
        }), style = MaterialTheme.typography.bodySmall)
        if (pack?.state == MapPackState.DOWNLOADING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { offline.pause(key) }) { TrailText(stringResource(R.string.cancel)) }
        } else if (pack?.state != MapPackState.COMPLETE) {
            OutlinedButton(onClick = { if (premiumAvailable()) offline.download(key, points) }, enabled = ready && premiumAvailable()) {
                TrailText(stringResource(R.string.map_download))
            }
        }
        if (pack != null) TextButton(onClick = { offline.delete(key) }) { TrailText(stringResource(R.string.map_delete)) }
        TrailText(OpenFreeMapProvider().attribution, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }) { TrailText(stringResource(R.string.map_credits)) }
        TextButton(onClick = { uri.openUri("https://openmaptiles.org/") }) { TrailText(stringResource(R.string.map_openmaptiles)) }
    }
    DisposableEffect(offline, key, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) offline.pause(key) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); offline.pause(key) }
    }
}

@Composable private fun NativeRouteMap(points: List<GeoPoint>, position: GeoPoint?, onFailure: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var failed by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf(false) }
    var nativeMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val map = remember {
        MapNetwork.initialise(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).localIdeographFontFamilyEnabled(true).localIdeographFontFamily("sans-serif")).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true; onFailure() }
            addOnDidFinishRenderingMapListener { fully -> rendered = fully }
            getMapAsync { native ->
                nativeMap = native
                native.setStyle(Style.Builder().fromUri(OpenFreeMapProvider.STYLE_URL)) { style ->
                    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
                    style.addSource(GeoJsonSource("trailnest-route", line))
                    style.addLayer(LineLayer("trailnest-route-line", "trailnest-route").withProperties(lineColor("#285C4D"), lineWidth(5f)))
                    style.addSource(GeoJsonSource("trailnest-start", Point.fromLngLat(points.first().lon, points.first().lat)))
                    style.addLayer(CircleLayer("trailnest-start-dot", "trailnest-start").withProperties(circleColor("#285C4D"), circleRadius(6f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                    style.addSource(GeoJsonSource("trailnest-finish", Point.fromLngLat(points.last().lon, points.last().lat)))
                    style.addLayer(CircleLayer("trailnest-finish-dot", "trailnest-finish").withProperties(circleColor("#8D7159"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f)))
                    style.addSource(GeoJsonSource("trailnest-position", FeatureCollection.fromFeatures(emptyArray<org.maplibre.geojson.Feature>())))
                    style.addLayer(CircleLayer("trailnest-position-dot", "trailnest-position").withProperties(circleColor("#287E83"), circleRadius(7f)))
                    val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
                    doOnLayout {
                        val padding = (48 * context.resources.displayMetrics.density).toInt()
                        native.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                    }
                    failed = false
                    styleReady = true
                }
            }
        }
    }
    val description = stringResource(R.string.map_title) + ". " + stringResource(R.string.map_legend)
    Box(Modifier.fillMaxWidth()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(320.dp).semantics { contentDescription = description }.testTag(if (rendered && !failed) "route-map-rendered" else "route-map-loading"))
        if (!rendered && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        TrailText(stringResource(R.string.map_attribution), Modifier.align(AbsoluteAlignment.BottomRight).background(MaterialTheme.colorScheme.surface.copy(alpha = .95f)).padding(4.dp), style = MaterialTheme.typography.labelSmall)
    }
    LaunchedEffect(nativeMap, styleReady, position) {
        if (styleReady) nativeMap?.style?.getSourceAs<GeoJsonSource>("trailnest-position")?.let { source ->
            if (position != null) source.setGeoJson(Point.fromLngLat(position.lon, position.lat))
            else source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<org.maplibre.geojson.Feature>()))
        }
    }
    if (failed) TrailText(stringResource(R.string.map_failed))
    DisposableEffect(map, owner) {
        var started = false
        var resumed = false
        fun start() { if (!started) { map.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { map.onResume(); resumed = true } }
        fun pause() { if (resumed) { map.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { map.onStop(); started = false } }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            else -> Unit
        } }
        owner.lifecycle.addObserver(observer)
        val callbacks = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(configuration: android.content.res.Configuration) = Unit
            override fun onLowMemory() { map.onLowMemory() }
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        else if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); context.applicationContext.unregisterComponentCallbacks(callbacks); stop(); map.onDestroy() }
    }
}
