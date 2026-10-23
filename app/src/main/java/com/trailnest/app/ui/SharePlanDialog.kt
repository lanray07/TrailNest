package com.trailnest.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable fun SharePlanDialog(route: ImportedRoute, imperial: Boolean, share: (String) -> Unit, dismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val estimate = ceil(route.metres / 4000 * 60).toInt().coerceAtLeast(1)
    var duration by rememberSaveable(route.id) { mutableStateOf(estimate.toString()) }
    var returnTime by rememberSaveable(route.id) { mutableStateOf(LocalDateTime.now().plusMinutes(estimate.toLong() + 30).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm"))) }
    var contact by rememberSaveable(route.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.share_plan_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(route.title)
            OutlinedTextField(duration, { duration = it }, label = { Text(stringResource(R.string.estimated_minutes)) })
            Text(stringResource(R.string.duration_estimate_hint))
            OutlinedTextField(returnTime, { returnTime = it }, label = { Text(stringResource(R.string.expected_return)) }, supportingText = { Text(stringResource(R.string.return_time_hint, zone.id)) })
            OutlinedTextField(contact, { contact = it.take(500) }, label = { Text(stringResource(R.string.optional_contact)) })
            Text(stringResource(R.string.share_plan_privacy))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            SharedWalkPlan(route.title, Gpx.points(route).first(), TrailMath.formatDistance(route.metres, imperial), duration.toIntOrNull() ?: 0, SharedWalkPlan.parseReturn(returnTime, zone), contact).text()
        }.onSuccess { share(it); dismiss() }.onFailure { error = it.message ?: "Check the plan details." }
    }) { Text(stringResource(R.string.open_share_options)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
