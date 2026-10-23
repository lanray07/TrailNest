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
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val zone = ZoneId.systemDefault()
    val estimate = ceil(route.metres / 4000 * 60).toInt().coerceAtLeast(1)
    var duration by rememberSaveable(route.id) { mutableStateOf(estimate.toString()) }
    var returnTime by rememberSaveable(route.id) { mutableStateOf(LocalDateTime.now().plusMinutes(estimate.toLong() + 30).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm"))) }
    var contact by rememberSaveable(route.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(modifier = Modifier.widthIn(max = (config.screenWidthDp - 32).coerceAtMost(560).dp).heightIn(max = (config.screenHeightDp - 48).coerceAtLeast(240).dp).fillMaxWidth(), properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = dismiss, title = { TrailText(stringResource(R.string.share_plan_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrailText(route.title)
            OutlinedTextField(duration, { duration = it }, label = { TrailText(stringResource(R.string.estimated_minutes)) })
            TrailText(stringResource(R.string.duration_estimate_hint))
            OutlinedTextField(returnTime, { returnTime = it }, textStyle = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), label = { TrailText(stringResource(R.string.expected_return)) }, supportingText = { TrailText(stringResource(R.string.return_time_hint, zone.id)) })
            OutlinedTextField(contact, { contact = it.take(500) }, label = { TrailText(stringResource(R.string.optional_contact)) })
            TrailText(stringResource(R.string.share_plan_privacy))
            error?.let { TrailText(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            SharedWalkPlan(route.title, Gpx.points(route).first(), context.distanceText(route.metres, imperial, locale), duration.toIntOrNull() ?: 0, SharedWalkPlan.parseReturn(returnTime, zone), contact).text(context.getString(R.string.shared_plan_body), context.getString(R.string.shared_plan_contact), locale = locale)
        }.onSuccess { share(it); dismiss() }.onFailure { error = context.userMessage(it, R.string.error_plan) }
    }) { TrailText(stringResource(R.string.open_share_options)) } }, dismissButton = { TextButton(onClick = dismiss) { TrailText(stringResource(R.string.cancel)) } })
}
