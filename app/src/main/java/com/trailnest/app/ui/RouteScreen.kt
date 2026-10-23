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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource

@Composable fun RouteScreen(route: ImportedRoute, fix: Location?, locating: Boolean, imperial: Boolean, voiceReady: Boolean, command: VoiceCommand, consume: () -> Unit, onLocation: () -> Unit, speak: (String) -> Unit, listen: () -> Unit, export: (ImportedRoute) -> Unit, share: (String) -> Unit, back: () -> Unit, notice: (String) -> Unit) {
    val points = remember(route.geometry) { Gpx.points(route) }
    var sharing by rememberSaveable(route.id) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(locating) { while (locating) { now = SystemClock.elapsedRealtimeNanos(); delay(3000) } }
    val fresh = fix != null && (now - fix.elapsedRealtimeNanos) / 1e9 < 30 && now >= fix.elapsedRealtimeNanos
    val progress = if (fresh && fix != null && fix.hasAccuracy()) TrailMath.progress(points, GeoPoint(fix.latitude, fix.longitude), fix.accuracy.toDouble()) else null
    val context = androidx.compose.ui.platform.LocalContext.current
    val status = when { !locating -> stringResource(R.string.location_paused); progress == null -> stringResource(R.string.location_waiting); progress.uncertain -> stringResource(R.string.location_uncertain); progress.offRoute -> stringResource(R.string.route_off_route); else -> stringResource(R.string.route_remaining, distanceText(progress.remainingMetres, imperial)) }
    LaunchedEffect(command) {
        when (command) {
            VoiceCommand.REPEAT, VoiceCommand.DISTANCE -> speak(status)
            VoiceCommand.PAUSE -> if (locating) onLocation()
            VoiceCommand.RESUME -> if (!locating) onLocation()
            VoiceCommand.SAVE -> notice(context.getString(R.string.voice_save_hint))
            else -> Unit
        }
        consume()
    }
    val latestLocating by rememberUpdatedState(locating)
    DisposableEffect(route.id) { onDispose { if (latestLocating) onLocation() } }
    Page(route.title, stringResource(R.string.route_description)) {
        TextButton(onClick = back) { Text(stringResource(R.string.ui_back_to_routes)) }
        Text(stringResource(R.string.route_summary, distanceText(route.metres, imperial), androidx.compose.ui.res.pluralStringResource(R.plurals.route_points, points.size, points.size)))
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
        Text(stringResource(R.string.ui_geometry_overview_green_start_brown_finish_teal_recent_position_n), style = MaterialTheme.typography.bodySmall)
        TrailPanel(Modifier.fillMaxWidth()) {
            Heading(stringResource(R.string.ui_your_route_at_a_glance), true); Text(status)
            if (fresh && fix != null) Text(stringResource(R.string.reported_accuracy, fix.accuracy.toInt()))
            Button(onClick = onLocation) { Text(stringResource(if (locating) R.string.pause_location else R.string.foreground_location)) }
            OutlinedButton(onClick = { speak(status) }, enabled = voiceReady) { Text(stringResource(R.string.ui_read_progress_aloud)) }
            TextButton(onClick = listen) { Text(stringResource(R.string.ui_push_to_talk)) }
            Text(stringResource(R.string.ui_no_validated_turn_instructions_are_included_spoken_output_reports))
        }
        OutlinedButton(onClick = { export(route) }) { Text(stringResource(R.string.ui_export_gpx)) }
        TextButton(onClick = { sharing = true }) { Text(stringResource(R.string.share_plan_title)) }
    }
    if (sharing) SharePlanDialog(route, imperial, share) { sharing = false }
}
