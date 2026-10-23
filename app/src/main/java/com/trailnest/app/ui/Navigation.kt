package com.trailnest.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Labels can grow vertically; narrow/large-font layouts use a full-width, scrollable menu. */
@Composable fun TrailScaffold(page: String, navigate: (String) -> Unit, content: @Composable () -> Unit) {
    val labels = listOf(stringResource(R.string.discover), stringResource(R.string.my_walks), stringResource(R.string.offline), stringResource(R.string.checklist), stringResource(R.string.settings))
    val keys = listOf("Discover", "My walks", "Offline packs", "Checklist", "Settings")
    val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.BookmarkBorder, Icons.Outlined.Download, Icons.Outlined.Checklist, Icons.Outlined.Settings)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val contentState = rememberSaveableStateHolder()
    val toggleFocus = remember { FocusRequester() }
    val itemFocus = remember { List(keys.size) { FocusRequester() } }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val sidebar = maxWidth >= 720.dp && maxHeight >= 480.dp && fontScale <= 1.3f
        BackHandler(expanded && !sidebar) { expanded = false }
        LaunchedEffect(expanded, sidebar) {
            if (!sidebar) {
                if (expanded) itemFocus[keys.indexOf(page).coerceAtLeast(0)].requestFocus()
                else toggleFocus.requestFocus()
            }
        }
        val currentLabel = labels[keys.indexOf(page).coerceAtLeast(0)]
        val menuAction = stringResource(if (expanded) R.string.navigation_close else R.string.navigation_open)
        val menuDescription = stringResource(R.string.accessibility_action_for, menuAction, currentLabel)
        val menuState = stringResource(if (expanded) R.string.accessibility_expanded else R.string.accessibility_collapsed)
        Column(Modifier.semantics { isTraversalGroup = true }.onPreviewKeyEvent {
            if (expanded && !sidebar && it.type == KeyEventType.KeyDown && it.key == Key.Escape) { expanded = false; true } else false
        }) {
            if (!sidebar) OutlinedButton(onClick = { expanded = !expanded }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp).focusRequester(toggleFocus).testTag("navigation-toggle").semantics { stateDescription = menuState; traversalIndex = -1f }) {
                Icon(if (expanded) Icons.Outlined.Close else Icons.Outlined.Menu, null)
                Spacer(Modifier.width(12.dp))
                TrailText(currentLabel, Modifier.weight(1f).clearAndSetSemantics { contentDescription = menuDescription })
            }
            Row(Modifier.weight(1f)) {
                if (sidebar || expanded) Column(Modifier.then(if (sidebar) Modifier.width(240.dp) else Modifier.fillMaxWidth()).fillMaxHeight().verticalScroll(rememberScrollState()).focusGroup().padding(16.dp).testTag("navigation-menu").semantics { isTraversalGroup = true; traversalIndex = 0f }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_trailnest), stringResource(R.string.app_name), Modifier.size(56.dp).align(Alignment.CenterHorizontally))
                    keys.forEachIndexed { index, key ->
                        val selected = page == key
                        OutlinedButton(onClick = { navigate(key); expanded = false }, Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(itemFocus[index]).testTag("nav-$key").semantics { role = Role.Tab; this.selected = selected; traversalIndex = index.toFloat() },
                            colors = if (selected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()) {
                            Icon(icons[index], null, Modifier.testTag("nav-icon-$key"))
                            Spacer(Modifier.width(12.dp))
                            TrailText(labels[index], Modifier.weight(1f).testTag("nav-label-$key"))
                        }
                    }
                    TrailText(stringResource(R.string.ui_a_brighter_outdoors_one_step_at_a_time), style = MaterialTheme.typography.bodySmall)
                }
                if (sidebar || !expanded) Box(Modifier.weight(1f).fillMaxHeight().testTag("screen-content").semantics { isTraversalGroup = true; traversalIndex = 1f }) {
                    contentState.SaveableStateProvider(page) { content() }
                }
            }
        }
    }
}
