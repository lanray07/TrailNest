package com.trailnest.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*

/** Measure allocated targets, including off-screen composed controls, without mistaking scroll clipping for sizing. */
fun ComposeContentTestRule.auditAccessibleControls(density: Float): JSONObject {
    val nodes = onAllNodes(hasClickAction()).fetchSemanticsNodes()
    assertTrue("Expected interactive controls", nodes.isNotEmpty())
    var visibleTouchBounds = 0
    var unplacedActions = 0
    var measuredActions = 0
    for (node in nodes) {
        val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        assertFalse("Unlabelled action: ${node.config}", name.isNullOrBlank())
        // Lazy lists can retain semantics for prefetched/reused items that have not
        // been placed. Their zero geometry is not an interactive touch target.
        if (!node.layoutInfo.isPlaced) { unplacedActions++; continue }
        val width = node.layoutInfo.width / density
        val height = node.layoutInfo.height / density
        assertTrue("Target smaller than 48dp: $name (${width}x$height)", width >= 47.9f && height >= 47.9f)
        measuredActions++
        // Scroll edges may clip an otherwise valid target. Check actual hit bounds when
        // the complete semantic target is visible and already at least 48dp itself.
        val visible = node.boundsInRoot
        if (node.size.width / density >= 47.9f && node.size.height / density >= 47.9f &&
            kotlin.math.abs(visible.width-node.size.width) < 1f && kotlin.math.abs(visible.height-node.size.height) < 1f) {
            val touch = node.touchBoundsInRoot
            assertTrue("Visible touch bounds: $name",touch.width / density >= 47.9f && touch.height / density >= 47.9f)
            visibleTouchBounds++
        }
    }
    assertTrue("Expected placed interactive targets", measuredActions > 0)
    return JSONObject().put("labelledActions", nodes.size).put("measuredTargets",measuredActions).put("unplacedActions",unplacedActions).put("minimumAllocatedTargetDp",48).put("fullyVisibleTouchBoundsChecked",visibleTouchBounds)
}

/** Keyboard order is a separate contract from accessibility focus. Exercise both on the actual device. */
@OptIn(ExperimentalTestApi::class)
fun ComposeContentTestRule.auditNavigationFocus(labels: List<String>, keyboardMode: () -> String): JSONObject {
    val keys = listOf("Discover","My walks","Offline packs","Checklist","Settings")
    // Android buttons intentionally reject keyboard focus while the window is in touch mode.
    InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    var mode = ""
    runOnIdle { mode = keyboardMode() }
    waitForIdle()
    onNodeWithTag("nav-Discover").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue("Request keyboard focus ($mode)",it()) }
    for ((index,key) in keys.withIndex()) {
        onNodeWithTag("nav-$key").assertIsFocused()
        onNodeWithTag("nav-$key").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab))
        onNodeWithTag("nav-$key").assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex,index.toFloat()))
        if (index < keys.lastIndex) onNodeWithTag("nav-$key").performKeyInput { pressKey(Key.Tab) }
    }
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val info = automation.serviceInfo
    val originalFlags = info.flags
    var focused = 0
    var nativeOrderEdges = 0
    try {
        info.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        automation.serviceInfo = info
        for ((index,key) in keys.withIndex()) {
            if (index < keys.lastIndex) onNodeWithTag("nav-${keys[index+1]}").performScrollTo()
            onNodeWithTag("nav-$key").performScrollTo()
            waitForIdle()
            automation.waitForIdle(50,3000)
            val root = automation.rootInActiveWindow ?: error("No Android accessibility root")
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo) { nodes += node; for (i in 0 until node.childCount) node.getChild(i)?.let(::visit) }
            visit(root)
            try {
                val target = nodes.singleOrNull { it.viewIdResourceName == "nav-$key" } ?: error("Navigation missing from Android accessibility tree: $key")
                fun spokenName(node: AccessibilityNodeInfo): String {
                    val own = listOfNotNull(node.text,node.contentDescription).joinToString(" ")
                    return own + (0 until node.childCount).joinToString(" ") { childIndex ->
                        node.getChild(childIndex)?.let { child -> try { spokenName(child) } finally { child.recycle() } } ?: ""
                    }
                }
                val exportedName = spokenName(target)
                assertTrue("Native label $key: '$exportedName'; $target", exportedName.contains(labels[index]))
                assertEquals("Native selected destination $key",index == 0,target.isSelected)
                // Android intentionally omits reactivation for an already-selected tab.
                if (index > 0) assertTrue("Native navigation action $key: $target",target.isClickable && target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                assertFalse("Tabs must not be announced as checkboxes",target.isCheckable)
                if (index < keys.lastIndex) {
                    // Android uses child order unless an explicit traversal override is present.
                    val currentPosition = nodes.indexOf(target)
                    val nextPosition = nodes.indexOfFirst { it.viewIdResourceName == "nav-${keys[index+1]}" }
                    assertTrue("Android child order $key -> ${keys[index+1]}",nextPosition > currentPosition)
                    target.traversalBefore?.let { before ->
                        try {
                            val destination = keys.indexOfFirst { before.viewIdResourceName == "nav-$it" }
                            assertTrue("Backward Android traversal override: $key",destination < 0 || destination > index)
                        } finally { before.recycle() }
                    }
                    nativeOrderEdges++
                }
                assertTrue("Native accessibility focus rejected: $key",target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                automation.waitForIdle(50,3000)
                // Query the virtual node itself: global findFocus may return only the
                // embedding Android view for a nested ComposeView on this API level.
                waitUntil(3000) { target.refresh() && target.isAccessibilityFocused }
                focused++
            } finally { nodes.forEach { it.recycle() } }
        }
    } finally { info.flags = originalFlags; automation.serviceInfo = info }
    return JSONObject().put("keyboardOrder",keys.joinToString(">")).put("nativeAccessibilityFocusTargets",focused).put("nativeFocusVerification","refreshed virtual-node accessibility focus flag").put("nativeTraversalPairs",nativeOrderEdges).put("nativeTraversalMethod","child order and non-conflicting overrides").put("explicitTraversalIndices",true)
}
