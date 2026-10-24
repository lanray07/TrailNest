package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val tags = remember { AppLanguage.supportedTags(context) }
    var open by remember { mutableStateOf(false) }
    val deviceLabel = stringResource(R.string.language_device)
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("language-picker")) {
        Column(Modifier.weight(1f)) {
            TrailText(stringResource(R.string.language_title))
            TrailText(if (selected.isEmpty()) deviceLabel else AppLanguage.nativeName(selected),
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { DialogTitle(stringResource(R.string.language_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
                (listOf("") + tags).forEach { tag ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("language-option-${tag.ifEmpty { "device" }}")
                        .selectable(selected == tag, role = Role.RadioButton, onClick = { open = false; onSelect(tag) })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == tag, onClick = null)
                        TrailText(if (tag.isEmpty()) deviceLabel else AppLanguage.nativeName(tag), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                    }
                }
            }
        }, confirmButton = {}, dismissButton = {
            TextButton(onClick = { open = false }) { TrailText(stringResource(R.string.cancel)) }
        })
}
