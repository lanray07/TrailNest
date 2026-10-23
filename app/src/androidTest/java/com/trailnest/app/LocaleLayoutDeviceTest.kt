package com.trailnest.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.data.ImportedRoute
import com.trailnest.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/** Real production composables, synthetic data, and local overrides: no device settings or user DB writes. */
class LocaleLayoutDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    data class Case(val tag: String, val width: Int, val scale: Float, val screen: String = "Discover")
    private val tags = listOf("en-US","en-GB","en-AU","en-IN","fr","de","it","es","es-MX","pt-BR","pt-PT","nl","pl","sv","ja","ar","hi","zh-Hans","ko")
    @Test fun localeFontAndViewportMatrix() {
        Assume.assumeTrue("Run against the isolated translationPreview APK", BuildConfig.DRAFT_TRANSLATIONS)
        prepareTestWindow { compose.activity }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val output = File(app.getExternalFilesDir(null), "layout-checks").apply { mkdirs() }
        val baseline = InstrumentationRegistry.getArguments().getString("layoutSmoke") == "true"
        val languages = if (baseline) listOf("ar", "de") else tags
        val results = JSONArray()
        val failures = mutableListOf<String>()
        val overflows = ConcurrentLinkedQueue<String>()
        var current by mutableStateOf(Case(languages.first(), 360, 2f))
        var page by mutableStateOf("Discover")
        val fixture = ImportedRoute("layout-fixture", "QA GPX 51.5000, -0.1000", "51.5,-0.1;51.501,-0.099", 120.0, 0L, "fixture", false)
        compose.setContent {
            val host = LocalContext.current
            val hostConfig = LocalConfiguration.current
            val density = LocalDensity.current.density
            val config = remember(hostConfig, current.tag, current.width, current.scale) {
                Configuration(hostConfig).apply {
                    setLocale(Locale.forLanguageTag(current.tag)); setLayoutDirection(Locale.forLanguageTag(current.tag))
                    fontScale = current.scale; screenWidthDp = current.width; screenHeightDp = 640
                }
            }
            val context = remember(config) { android.view.ContextThemeWrapper(host, R.style.Theme_TrailNest).apply { applyOverrideConfiguration(config) } }
            val scene: @Composable () -> Unit = { CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(density, current.scale),
                LocalLayoutDirection provides if (current.tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalTextLayoutAudit provides { text, result -> if (result.hasVisualOverflow) overflows.add(text.take(120)) }) {
                MaterialTheme {
                    Box(Modifier.requiredSize(current.width.dp, config.screenHeightDp.dp).testTag("layout-viewport")) {
                        key(current) {
                            TrailScaffold(page, { page = it }) {
                                when (current.screen) {
                                    "Discover" -> DiscoverScreen({}, {})
                                    "My walks" -> JournalScreen(emptyList(), emptyList(), false, false, {}, {}, { _, _ -> }, {}, recording = { RecordingPanel(RecordingDraft("fixture", 0, 61_000, 120.0), false, false, {}, { _, _ -> }) })
                                    "Checklist" -> ChecklistScreen(PersonalChecklist.initial().mapIndexed { i, item -> item.copy(title = context.checklistDefault(i)) }, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, {})
                                    "Offline packs" -> PacksScreen(listOf(fixture), false, {}, {}, {}, {})
                                    "Settings" -> SettingsScreen(emptyPreferences(), {}, { _, _ -> }, false)
                                    "Coverage" -> CoverageScreen()
                                    "Route" -> RouteScreen(fixture, null, false, false, false, VoiceCommand.UNKNOWN, {}, {}, {}, {}, {}, {}, {}, {})
                                    "Share" -> SharePlanDialog(fixture, false, {}) {}
                                    "Recording" -> Column(Modifier.verticalScroll(rememberScrollState())) { RecordingPanel(RecordingDraft("fixture", 0, 61_000, 120.0), false, false, {}, { _, _ -> }) }
                                }
                            }
                        }
                    }
                }
            } }
            // Dialogs create Android composition locals from their hosting View's Context.
            // A real configured ComposeView makes the same locale/font apply to those windows.
            key(config) {
                AndroidView(factory = { ComposeView(context).apply { setContent(scene) } },
                    modifier = Modifier.requiredSize(current.width.dp, 640.dp))
            }
        }
        val screens = listOf("Discover", "My walks", "Checklist", "Offline packs", "Settings", "Coverage", "Route", "Share", "Recording")
        for (tag in languages) for (width in listOf(360, 800)) for (scale in if (baseline) listOf(2f) else listOf(1f, 2f)) {
            val id = "$tag-$width-$scale"
            try {
                compose.runOnIdle { overflows.clear(); page = "Discover"; current = Case(tag, width, scale) }
                compose.waitForIdle()
                compose.onNodeWithTag("layout-viewport").assertWidthIsEqualTo(width.dp).assertHeightIsEqualTo(640.dp)
                if (compose.onAllNodesWithTag("navigation-toggle").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("navigation-toggle").performClick()
                compose.onNodeWithTag("nav-Discover").performScrollTo().assertIsDisplayed()
                val icon = compose.onNodeWithTag("nav-icon-Discover", true).fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("nav-label-Discover", true).fetchSemanticsNode().boundsInRoot
                Assert.assertTrue("Navigation direction $id", if (tag == "ar") icon.center.x > label.center.x else icon.center.x < label.center.x)
                compose.onNodeWithTag("nav-Settings").performScrollTo().assertIsDisplayed().performClick()
                for (screen in screens) {
                    compose.runOnIdle { current = Case(tag, width, scale, screen); page = if (screen in screens.take(5)) screen else "Offline packs" }
                    compose.waitForIdle()
                    val strings = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
                    if (screen == "My walks") {
                        compose.onNodeWithTag("journal-list").performScrollToNode(hasText(strings.getString(R.string.record_walk)))
                        compose.onNodeWithText(strings.getString(R.string.record_walk)).performClick()
                        compose.onNodeWithTag("journal-list").performScrollToNode(hasText(strings.getString(R.string.save_walk)))
                    }
                    if (screen == "Recording") {
                        compose.onNodeWithText(strings.getString(R.string.finish_recording)).performScrollTo().performClick()
                        compose.onNodeWithText(strings.getString(R.string.save_walk)).performScrollTo().assertExists()
                    }
                    if (screen == "Share") {
                        compose.onNodeWithText(strings.getString(R.string.share_plan_title)).assertIsDisplayed()
                        compose.onNodeWithText(strings.getString(R.string.expected_return)).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText(strings.getString(R.string.open_share_options)).assertIsDisplayed()
                    }
                    val messages = overflows.toSet()
                    if (messages.isNotEmpty()) { failures += "$id/$screen: ${messages.joinToString()}"; overflows.clear() }
                    if (scale == 2f && tag in listOf("ar", "de", "ja", "hi") && screen in listOf("Discover", "Settings", "Share")) {
                        // Allow the physical display to present the frame. PixelCopy of Compose roots on
                        // this Fire OS can return a stale/cropped activity buffer, especially for dialogs.
                        instrumentation.waitForIdleSync()
                        Thread.sleep(120)
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        File(output, "$id-$screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                results.put(JSONObject().put("locale",tag).put("widthDp",width).put("heightDp",640).put("fontScale",scale).put("screens",JSONArray(screens)).put("status",if(failures.any { it.startsWith(id) }) "failed" else "passed"))
            } catch (error: Throwable) {
                failures += "$id/${current.screen}: ${error.message} at ${error.stackTrace.firstOrNull { it.className == javaClass.name }}"
                results.put(JSONObject().put("locale",tag).put("widthDp",width).put("fontScale",scale).put("screen",current.screen).put("status","failed").put("error",error.message))
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(output,"$id-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
            }
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "Layout matrix: $id complete\n") })
            File(output,"matrix.json").writeText(JSONObject().put("configurations",results).put("failures",JSONArray(failures)).put("device",android.os.Build.MODEL).put("api",android.os.Build.VERSION.SDK_INT).toString(2))
        }
        Assert.assertTrue(failures.take(12).joinToString("\n"), failures.isEmpty())
    }
}
