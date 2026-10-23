package com.trailnest.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.view.inspector.WindowInspectorCompat
import com.trailnest.app.data.ImportedRoute
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale

/** Uses the real keyboard window and screen coordinates, not just Compose visibility. */
class ShareKeyboardDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val tags = listOf("en-US","en-GB","en-AU","en-IN","fr","de","it","es","es-MX","pt-BR","pt-PT","nl","pl","sv","ja","ar","hi","zh-Hans","ko")
    data class Case(val tag: String, val width: Int, val scale: Float)

    @Test fun actionsAreTouchableAboveOpenKeyboard() {
        Assume.assumeTrue("Draft package isolates all synthetic fixtures",BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.setInTouchMode(true)
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val app = instrumentation.targetContext
        val output = File(app.getExternalFilesDir(null),"share-keyboard-checks").apply { mkdirs() }
        val smoke = InstrumentationRegistry.getArguments().getString("layoutSmoke") == "true"
        val languages = if(smoke) listOf("ar","de") else tags
        val results = JSONArray()
        val failures = mutableListOf<String>()
        var current by mutableStateOf(Case(languages.first(),360,2f))
        var open by mutableStateOf(true)
        var submitted = ""
        var dismissals = 0
        val fixture = ImportedRoute("keyboard-fixture","QA GPX","51.5,-0.1;51.501,-0.099",120.0,0L,"fixture",false)
        compose.setContent {
            val host = LocalContext.current
            val hostConfig = LocalConfiguration.current
            val density = LocalDensity.current.density
            val config = remember(hostConfig,current) { Configuration(hostConfig).apply {
                setLocale(Locale.forLanguageTag(current.tag));setLayoutDirection(Locale.forLanguageTag(current.tag))
                fontScale=current.scale;screenWidthDp=current.width;screenHeightDp=640
            } }
            val context = remember(config) { android.view.ContextThemeWrapper(host,R.style.Theme_TrailNest).apply { applyOverrideConfiguration(config) } }
            key(config) {
                AndroidView(factory={ androidx.compose.ui.platform.ComposeView(context).apply { setContent {
                  CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,
                    LocalDensity provides Density(density,current.scale),
                    LocalLayoutDirection provides if(current.tag=="ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    MaterialTheme {
                        Box(Modifier.requiredSize(current.width.dp,640.dp)) {
                            if(open) SharePlanDialog(fixture,false,{ submitted=it },{ dismissals++;open=false })
                        }
                    }
                  }
                } } },modifier=Modifier.requiredSize(current.width.dp,640.dp))
            }
        }
        fun keyboardBounds(): Rect? {
            val windows = automation.windows
            try {
                return windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { window -> Rect().also { window.getBoundsInScreen(it) }.takeIf { !it.isEmpty } }
            } finally { windows.forEach { it.recycle() } }
        }
        fun openKeyboard(tag: String) {
            // Fire OS requires the dialog itself (not just its activity) to opt in
            // before drawing an IME over the lock screen. Only synthetic draft windows.
            instrumentation.runOnMainSync {
                WindowInspectorCompat.getGlobalWindowViews().forEach { view ->
                    val params=view.layoutParams as? WindowManager.LayoutParams
                    if(params?.type==WindowManager.LayoutParams.TYPE_APPLICATION) {
                        params.flags=params.flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view,params)
                    }
                }
            }
            compose.onNodeWithTag(tag).performScrollTo().performTouchInput { click() }
            compose.waitUntil(5000) { keyboardBounds() != null }
            // Let the Android window/insets and any IME animation settle before measuring.
            instrumentation.waitForIdleSync();Thread.sleep(250);compose.waitForIdle()
        }
        fun touchAction(tag: String, label: String): JSONObject {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            compose.waitForIdle();Thread.sleep(120)
            val ime = keyboardBounds() ?: error("Keyboard must remain open for $tag")
            val root = automation.rootInActiveWindow ?: error("No active dialog root")
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) { nodes+=n;for(i in 0 until n.childCount)n.getChild(i)?.let(::visit) }
            visit(root)
            try {
                val text = nodes.firstOrNull { it.text?.toString()==label || it.contentDescription?.toString()==label } ?: error("Native action label missing: $label")
                var button = text
                val parents = mutableListOf<AccessibilityNodeInfo>()
                try {
                    while(!button.isClickable) { button=button.parent?.also { parents+=it } ?: error("Native button missing: $label") }
                    val bounds=Rect().also { button.getBoundsInScreen(it) }
                    assertTrue("Action is not visible: $tag",button.isVisibleToUser)
                    assertTrue("Action overlaps keyboard: $tag $bounds, IME $ime",bounds.bottom<=ime.top && bounds.top>=0 && !bounds.isEmpty)
                    val density=app.resources.displayMetrics.density
                    assertTrue("Native target smaller than 48dp: $tag $bounds",bounds.width()/density>=47.9f && bounds.height()/density>=47.9f)
                    if(tag=="share-submit" && current.scale==2f && current.tag in listOf("ar","de","hi","ja")) {
                        automation.takeScreenshot()?.let { bitmap -> File(output,"${current.tag}-${current.width}-${current.scale}-share-keyboard.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
                    }
                    val now=SystemClock.uptimeMillis()
                    for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                        val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                        event.source=InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue("Touch injection rejected",automation.injectInputEvent(event,true)) } finally { event.recycle() }
                    }
                    return JSONObject().put("action",tag).put("bounds",bounds.flattenToString()).put("keyboardBounds",ime.flattenToString()).put("realTouchInjected",true)
                } finally { parents.forEach { it.recycle() } }
            } finally { nodes.forEach { it.recycle() } }
        }
        try {
            for(tag in languages) for(width in listOf(360,800)) for(scale in if(smoke) listOf(2f) else listOf(1f,2f)) {
                val id="$tag-$width-$scale"
                val checks=JSONArray()
                try {
                    compose.runOnIdle { open=false }
                    compose.waitForIdle()
                    compose.runOnIdle { current=Case(tag,width,scale);submitted="";dismissals=0;open=true }
                    compose.waitForIdle()
                    val strings=app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
                    openKeyboard("share-duration")
                    compose.onNodeWithTag("share-duration").performTextReplacement("0")
                    checks.put(touchAction("share-submit",strings.getString(R.string.open_share_options)))
                    compose.waitForIdle()
                    compose.runOnIdle { assertEquals("",submitted);assertTrue(open);assertEquals(0,dismissals) }
                    compose.waitUntil(3000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error)).fetchSemanticsNodes().isNotEmpty() }
                    openKeyboard("share-duration")
                    compose.onNodeWithTag("share-duration").performTextReplacement("30")
                    compose.onNodeWithTag("share-duration").performImeAction()
                    compose.onNodeWithTag("share-return").assertIsFocused()
                    compose.onNodeWithTag("share-return").performImeAction()
                    compose.onNodeWithTag("share-contact").assertIsFocused().performTextReplacement("QA contact")
                    checks.put(touchAction("share-submit",strings.getString(R.string.open_share_options)))
                    compose.waitForIdle()
                    compose.runOnIdle { assertTrue("Contact lost",submitted.contains("QA contact"));assertFalse(open);assertEquals(1,dismissals) }
                    compose.runOnIdle { open=true }
                    compose.waitForIdle()
                    openKeyboard("share-contact")
                    if(scale==2f && tag in listOf("ar","de","hi","ja")) {
                        compose.onNodeWithTag("share-cancel").performScrollTo();compose.waitForIdle();Thread.sleep(120)
                        automation.takeScreenshot()?.let { bitmap -> File(output,"$id-keyboard.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
                    }
                    checks.put(touchAction("share-cancel",strings.getString(R.string.cancel)))
                    compose.waitForIdle()
                    compose.runOnIdle { assertFalse(open);assertEquals(2,dismissals) }
                    // Exercise dismissal separately on the first (RTL in smoke) case.
                    val dismissalChecks = results.length()==0
                    if(dismissalChecks) {
                        compose.runOnIdle { open=true };compose.waitForIdle()
                        openKeyboard("share-contact")
                        compose.onNodeWithTag("share-contact").performImeAction()
                        compose.waitUntil(5000) { keyboardBounds()==null }
                        compose.runOnIdle { assertTrue("Done must not dismiss",open) }
                        openKeyboard("share-contact")
                        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                        compose.waitUntil(5000) { keyboardBounds()==null }
                        compose.runOnIdle { assertTrue("First Back must only hide the IME",open) }
                        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                        compose.waitForIdle()
                        compose.runOnIdle { assertFalse(open);assertEquals(3,dismissals) }
                        compose.runOnIdle { open=true };compose.waitForIdle()
                        openKeyboard("share-contact")
                        val ime=keyboardBounds() ?: error("Keyboard required for outside tap")
                        val now=SystemClock.uptimeMillis()
                        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                            val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,20f,ime.top/2f,0)
                            event.source=InputDevice.SOURCE_TOUCHSCREEN
                            try { assertTrue(automation.injectInputEvent(event,true)) } finally { event.recycle() }
                        }
                        compose.waitForIdle()
                        compose.runOnIdle { assertFalse("Outside tap must dismiss",open);assertEquals(4,dismissals) }
                    }
                    results.put(JSONObject().put("configuration",id).put("status","passed").put("actions",checks).put("imeNextOrder","duration>return>contact").put("sharedContactPreserved",true).put("doneBackOutsideChecks",dismissalChecks))
                } catch(error:Throwable) {
                    failures+="$id: ${error.message}"
                    results.put(JSONObject().put("configuration",id).put("status","failed").put("error",error.message))
                    automation.takeScreenshot()?.let { bitmap -> File(output,"$id-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
                }
                instrumentation.sendStatus(2,Bundle().apply { putString("stream","Share keyboard: $id complete\n") })
                File(output,"matrix.json").writeText(JSONObject().put("configurations",results).put("failures",JSONArray(failures)).put("device",android.os.Build.MODEL).put("api",android.os.Build.VERSION.SDK_INT).toString(2))
            }
            assertTrue(failures.take(12).joinToString("\n"),failures.isEmpty())
        } finally { info.flags=originalFlags;automation.serviceInfo=info }
    }
}
