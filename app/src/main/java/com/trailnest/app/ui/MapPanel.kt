package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.FeatureCollection

@Composable fun MapPanel(digest: String, points: List<GeoPoint>, premiumAvailable: () -> Boolean, position: GeoPoint? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val uri = LocalUriHandler.current
    val offline = remember { OfflineMaps.get(context) }
    val packs by offline.packs.collectAsStateWithLifecycle()
    val ready by offline.ready.collectAsStateWithLifecycle()
    val key = "TNMAP1:$digest"
    val pack = packs[key]
    var show by rememberSaveable(digest) { mutableStateOf(false) }
    TrailPanel(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.map_title), true)
        TrailText(stringResource(R.string.map_privacy))
        val numbers = java.text.NumberFormat.getIntegerInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])
        TrailText(stringResource(R.string.map_budget, numbers.format(40), numbers.format(2800)), style = MaterialTheme.typography.bodySmall)
        if (!show) Button(onClick = { show = true }) { TrailText(stringResource(R.string.map_load)) }
        if (show) NativeRouteMap(points, position)
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

@Composable private fun NativeRouteMap(points: List<GeoPoint>, position: GeoPoint?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var failed by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf(false) }
    var nativeMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val map = remember {
        MapNetwork.initialise(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { failed = true }
            addOnDidFinishRenderingMapListener { fully -> rendered = fully }
            getMapAsync { native ->
                nativeMap = native
                native.setStyle(Style.Builder().fromUri(OpenFreeMapProvider.STYLE_URL)) { style ->
                    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lon, it.lat) })
                    style.addSource(GeoJsonSource("trailnest-route", line))
                    style.addLayer(LineLayer("trailnest-route-line", "trailnest-route").withProperties(lineColor("#285C4D"), lineWidth(5f)))
                    style.addSource(GeoJsonSource("trailnest-position", FeatureCollection.fromFeatures(emptyArray<org.maplibre.geojson.Feature>())))
                    style.addLayer(CircleLayer("trailnest-position-dot", "trailnest-position").withProperties(circleColor("#287E83"), circleRadius(7f)))
                    val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
                    native.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 32))
                    failed = false
                }
            }
        }
    }
    val description = stringResource(R.string.map_title)
    AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(320.dp).semantics { contentDescription = description }.testTag(if (rendered && !failed) "route-map-rendered" else "route-map-loading"), update = {
        nativeMap?.style?.getSourceAs<GeoJsonSource>("trailnest-position")?.let { source ->
            if (position != null) source.setGeoJson(Point.fromLngLat(position.lon, position.lat))
            else source.setGeoJson(FeatureCollection.fromFeatures(emptyArray<org.maplibre.geojson.Feature>()))
        }
    })
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
