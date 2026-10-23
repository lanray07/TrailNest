package com.trailnest.app

import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable fun RouteScreen(route: ImportedRoute, fix: Location?, locating: Boolean, imperial: Boolean, voiceReady: Boolean, command: VoiceCommand, consume: () -> Unit, onLocation: () -> Unit, speak: (String) -> Unit, listen: () -> Unit, export: (ImportedRoute) -> Unit, share: (String) -> Unit, back: () -> Unit, notice: (String) -> Unit) {
    val points = remember(route.geometry) { Gpx.points(route) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(locating) { while (locating) { now = SystemClock.elapsedRealtimeNanos(); delay(3000) } }
    val fresh = fix != null && (now - fix.elapsedRealtimeNanos) / 1e9 < 30 && now >= fix.elapsedRealtimeNanos
    val progress = if (fresh && fix != null && fix.hasAccuracy()) TrailMath.progress(points, GeoPoint(fix.latitude, fix.longitude), fix.accuracy.toDouble()) else null
    val status = when { !locating -> "Location paused."; progress == null -> "Waiting for a recent location fix."; progress.uncertain -> "Location accuracy is poor. Position is uncertain."; progress.offRoute -> "You appear to be off the planned route. Check your surroundings."; else -> "Approximately ${TrailMath.formatDistance(progress.remainingMetres, imperial)} remaining." }
    LaunchedEffect(command) {
        when (command) {
            VoiceCommand.REPEAT, VoiceCommand.DISTANCE -> speak(status)
            VoiceCommand.PAUSE -> if (locating) onLocation()
            VoiceCommand.RESUME -> if (!locating) onLocation()
            VoiceCommand.SAVE -> notice("Use Record a walk in your journal to save distance, duration and notes.")
            else -> Unit
        }
        consume()
    }
    val latestLocating by rememberUpdatedState(locating)
    DisposableEffect(route.id) { onDispose { if (latestLocating) onLocation() } }
    Page(route.title, "Private imported route · Access and directions unreviewed") {
        TextButton(onClick = back) { Text("Back to routes") }
        Text("${TrailMath.formatDistance(route.metres, imperial)} · ${points.size} points")
        Canvas(Modifier.fillMaxWidth().height(260.dp).background(Sage)) {
            val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
            val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
            val correction = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
            val dx = ((maxLon - minLon) * correction).coerceAtLeast(0.00001); val dy = (maxLat - minLat).coerceAtLeast(0.00001)
            val scale = minOf((size.width - 48) / dx, (size.height - 48) / dy)
            val left = (size.width - dx * scale) / 2; val top = (size.height - dy * scale) / 2
            fun offset(p: GeoPoint) = Offset((left + (p.lon - minLon) * correction * scale).toFloat(), (top + (maxLat - p.lat) * scale).toFloat())
            val path = Path(); points.forEachIndexed { i, p -> val pos = offset(p); if (i == 0) path.moveTo(pos.x, pos.y) else path.lineTo(pos.x, pos.y) }
            drawPath(path, Forest, style = Stroke(width = 5.dp.toPx()))
            drawCircle(Forest, 8.dp.toPx(), offset(points.first())); drawCircle(Color(0xFF8D7159), 8.dp.toPx(), offset(points.last()))
            if (fresh && fix != null) { val pos = offset(GeoPoint(fix.latitude, fix.longitude)); if (pos.x in 0f..size.width && pos.y in 0f..size.height) drawCircle(Color(0xFF287E83), 7.dp.toPx(), pos) }
        }
        Text("Geometry overview · Green: start · Brown: finish · Teal: recent position. No basemap. North is up; scale varies with route bounds.", style = MaterialTheme.typography.bodySmall)
        TrailPanel(Modifier.fillMaxWidth()) {
            Heading("Your route, at a glance.", true); Text(status)
            if (fresh && fix != null) Text("Reported accuracy: ±${fix.accuracy.toInt()} m")
            Button(onClick = onLocation) { Text(if (locating) "Pause location" else "Use location while app is open") }
            OutlinedButton(onClick = { speak(status) }, enabled = voiceReady) { Text("Read progress aloud") }
            TextButton(onClick = listen) { Text("Push to talk") }
            Text("No validated turn instructions are included. Spoken output reports progress and uncertainty only. This view does not replace a map or access checks.")
        }
        OutlinedButton(onClick = { export(route) }) { Text("Export GPX") }
        TextButton(onClick = { val first = points.first(); share("TrailNest walk plan\n${route.title}\nStart: ${String.format(Locale.ROOT, "%.6f, %.6f", first.lat, first.lon)}\nDistance: ${TrailMath.formatDistance(route.metres, imperial)}\nAccess unreviewed. Expected return time: please add before sending. This is a static plan, with no live monitoring.") }) { Text("Share walk plan") }
    }
}
