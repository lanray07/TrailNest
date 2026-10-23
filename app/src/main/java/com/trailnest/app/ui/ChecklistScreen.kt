package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trailnest.app.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChecklistScreen(items: List<ChecklistItem>, check: (String) -> Unit, add: (String) -> Unit, rename: (String, String) -> Unit, remove: (String) -> Unit, move: (String, Int) -> Unit, reset: () -> Unit, restore: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restorePrompt by remember { mutableStateOf(false) }
    var removePrompt by remember { mutableStateOf<ChecklistItem?>(null) }
    val complete = items.count { it.checked }
    Page(stringResource(R.string.checklist_title), stringResource(R.string.checklist_description)) {
        TrailText(pluralStringResource(R.plurals.checklist_progress, items.size, complete, items.size))
        LinearProgressIndicator(progress = { if (items.isEmpty()) 0f else complete.toFloat() / items.size }, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editing = !editing }) { TrailText(stringResource(if (editing) R.string.finish_editing else R.string.edit_checklist)) }
            TextButton(onClick = reset) { TrailText(stringResource(R.string.reset_checklist)) }
        }
        if (items.isEmpty()) TrailText(stringResource(R.string.empty_checklist))
        items.forEachIndexed { index, item ->
            Column {
                Row(Modifier.fillMaxWidth().toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { check(item.id) }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.checked, onCheckedChange = null)
                    TrailText(item.title, Modifier.weight(1f))
                }
                if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { renameId = item.id; draft = item.title }) { TrailText(stringResource(R.string.rename_item)) }
                    TextButton(onClick = { move(item.id, -1) }, enabled = index > 0) { TrailText(stringResource(R.string.move_up)) }
                    TextButton(onClick = { move(item.id, 1) }, enabled = index < items.lastIndex) { TrailText(stringResource(R.string.move_down)) }
                    TextButton(onClick = { removePrompt = item }) { TrailText(stringResource(R.string.remove_item)) }
                }
            }
        }
        if (editing) TrailPanel {
            OutlinedTextField(draft, { draft = it.take(120); error = null }, label = { TrailText(stringResource(R.string.checklist_item_name)) }, modifier = Modifier.fillMaxWidth())
            error?.let { TrailText(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching { PersonalChecklist.validateTitle(draft) }.onSuccess {
                    if (renameId == null) add(it) else rename(renameId!!, it)
                    draft = ""; renameId = null
                }.onFailure { error = context.userMessage(it, R.string.error_checklist) }
            }, enabled = items.size < 100 || renameId != null) { TrailText(stringResource(if (renameId == null) R.string.add_item else R.string.save_changes)) }
            if (renameId != null) TextButton(onClick = { renameId = null; draft = "" }) { TrailText(stringResource(R.string.cancel)) }
            TextButton(onClick = { restorePrompt = true }) { TrailText(stringResource(R.string.restore_defaults)) }
        }
        TrailText(stringResource(R.string.checklist_safety))
    }
    if (restorePrompt) AlertDialog(onDismissRequest = { restorePrompt = false }, title = { TrailText(stringResource(R.string.restore_defaults)) }, text = { TrailText(stringResource(R.string.restore_defaults_warning)) }, confirmButton = { TextButton(onClick = { restore(); restorePrompt = false; renameId = null; draft = "" }) { TrailText(stringResource(R.string.restore_defaults)) } }, dismissButton = { TextButton(onClick = { restorePrompt = false }) { TrailText(stringResource(R.string.cancel)) } })
    removePrompt?.let { item -> AlertDialog(onDismissRequest = { removePrompt = null }, title = { TrailText(stringResource(R.string.remove_item)) }, text = { TrailText(item.title) }, confirmButton = { TextButton(onClick = { remove(item.id); removePrompt = null; if (renameId == item.id) { renameId = null; draft = "" } }) { TrailText(stringResource(R.string.remove_item)) } }, dismissButton = { TextButton(onClick = { removePrompt = null }) { TrailText(stringResource(R.string.cancel)) } }) }
}
