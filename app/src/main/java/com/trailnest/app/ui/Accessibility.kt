package com.trailnest.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*

/** Keep the visible action short while its merged button names the item it affects. */
@Composable fun ContextActionText(action: String, subject: String) {
    val description = stringResource(R.string.accessibility_action_for, action, subject)
    TrailText(action, Modifier.clearAndSetSemantics { contentDescription = description })
}

@Composable fun FormError(message: String) {
    TrailText(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite; error(message) }, color = MaterialTheme.colorScheme.error)
}

@Composable fun DialogTitle(text: String) { TrailText(text, Modifier.semantics { heading() }) }
