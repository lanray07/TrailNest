package com.trailnest.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable fun SharePlanDialog(route: ImportedRoute, imperial: Boolean, share: (String) -> Unit, dismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val title = stringResource(R.string.share_plan_title)
    val zone = ZoneId.systemDefault()
    val estimate = ceil(route.metres / 4000 * 60).toInt().coerceAtLeast(1)
    var duration by rememberSaveable(route.id) { mutableStateOf(estimate.toString()) }
    var returnTime by rememberSaveable(route.id) { mutableStateOf(LocalDateTime.now().plusMinutes(estimate.toLong() + 30).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm"))) }
    var contact by rememberSaveable(route.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
      val focus = LocalFocusManager.current
      val keyboard = LocalSoftwareKeyboardController.current
      var surfaceBounds by remember { mutableStateOf(Rect.Zero) }
      // Insets belong to the dialog window. The entire surface can scroll even when
      // large headings/actions exceed the space left above a landscape keyboard.
      Box(Modifier.fillMaxSize().pointerInput(dismiss) { detectTapGestures { if (!surfaceBounds.contains(it)) dismiss() } }.safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = (config.screenWidthDp - 32).coerceAtMost(560).coerceAtLeast(1).dp).fillMaxWidth().heightIn(max = (config.screenHeightDp - 48).coerceAtLeast(1).dp).onGloballyPositioned { surfaceBounds = it.boundsInRoot() }.semantics { paneTitle = title }, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
          Column(Modifier.testTag("share-scroll").verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall) { DialogTitle(title) }
            TrailText(route.title)
            OutlinedTextField(duration, { duration = it }, modifier = Modifier.fillMaxWidth().testTag("share-duration"), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }), label = { TrailText(stringResource(R.string.estimated_minutes)) })
            TrailText(stringResource(R.string.duration_estimate_hint))
            OutlinedTextField(returnTime, { returnTime = it }, modifier = Modifier.fillMaxWidth().testTag("share-return"), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }), textStyle = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), label = { TrailText(stringResource(R.string.expected_return)) }, supportingText = { TrailText(stringResource(R.string.return_time_hint, zone.id)) })
            OutlinedTextField(contact, { contact = it.take(500) }, modifier = Modifier.fillMaxWidth().testTag("share-contact"), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }), label = { TrailText(stringResource(R.string.optional_contact)) })
            TrailText(stringResource(R.string.share_plan_privacy))
            error?.let { FormError(it) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
              TextButton(onClick = dismiss, modifier = Modifier.testTag("share-cancel")) { TrailText(stringResource(R.string.cancel)) }
              TextButton(modifier = Modifier.testTag("share-submit"), onClick = {
        runCatching {
            SharedWalkPlan(route.title, Gpx.points(route).first(), context.distanceText(route.metres, imperial, locale), duration.toIntOrNull() ?: 0, SharedWalkPlan.parseReturn(returnTime, zone), contact).text(context.getString(R.string.shared_plan_body), context.getString(R.string.shared_plan_contact), locale = locale)
        }.onSuccess { share(it); dismiss() }.onFailure { error = context.userMessage(it, R.string.error_plan) }
              }) { TrailText(stringResource(R.string.open_share_options)) }
            }
          }
        }
      }
    }
}
