package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.trailnest.app.domain.*
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RecordingPanel(draft: RecordingDraft?, imperial: Boolean, busy: Boolean, action: (String) -> Unit, save: (String, String) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var review by rememberSaveable(draft?.id) { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var title by rememberSaveable(draft?.id) { mutableStateOf("") }
    var notes by rememberSaveable(draft?.id) { mutableStateOf("") }
    TrailPanel(Modifier.fillMaxWidth().testTag("live-recording")) {
        Heading(stringResource(if (draft?.running == true) R.string.recording_active else R.string.recording_channel), true)
        if (draft == null) {
            TrailText(stringResource(R.string.recording_description))
            TrailText(stringResource(R.string.recording_notifications))
            Button(onClick = { action("start") }, enabled = !busy) { TrailText(stringResource(R.string.start_recording)) }
        } else {
            val seconds = draft.activeMillis / 1000
            TrailText(stringResource(R.string.recording_measurements, distanceText(draft.metres, imperial),
                String.format(locale, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)))
            if (!draft.running) TrailText(stringResource(R.string.recording_paused))
            if (draft.interrupted) TrailText(stringResource(R.string.recording_interrupted))
            if (draft.running && !draft.hasFix) TrailText(stringResource(R.string.recording_waiting))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { review = false; action(if (draft.running) "pause" else "start") }, enabled = !busy) {
                    TrailText(stringResource(if (draft.running) R.string.pause_recording else R.string.resume_recording))
                }
                Button(onClick = { action("pause"); review = true }, enabled = !busy) { TrailText(stringResource(R.string.finish_recording)) }
                TextButton(onClick = { discard = true }, enabled = !busy) { TrailText(stringResource(R.string.discard_recording)) }
            }
            if (review && !draft.running) {
                TrailText(stringResource(R.string.recording_save_hint))
                OutlinedTextField(title, { title = it.take(120) }, label = { TrailText(stringResource(R.string.walk_title)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it.take(5000) }, label = { TrailText(stringResource(R.string.walk_notes)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { save(title, notes) }, enabled = !busy && title.isNotBlank()) { TrailText(stringResource(R.string.save_walk)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        TrailText(stringResource(R.string.recording_privacy), style = MaterialTheme.typography.bodySmall)
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { TrailText(stringResource(R.string.discard_recording)) },
        text = { TrailText(stringResource(R.string.discard_recording_body)) },
        confirmButton = { TextButton(onClick = { discard = false; action("discard") }, enabled = !busy) { TrailText(stringResource(R.string.discard_recording)) } },
        dismissButton = { TextButton(onClick = { discard = false }) { TrailText(stringResource(R.string.cancel)) } })
}
