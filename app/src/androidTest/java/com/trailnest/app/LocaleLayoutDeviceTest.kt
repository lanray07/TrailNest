package com.trailnest.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/** Real production composables, synthetic data, and local overrides: no device settings or user DB writes. */
@OptIn(ExperimentalTestApi::class)
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
        val accessibility = JSONArray()
        val failures = mutableListOf<String>()
        val overflows = ConcurrentLinkedQueue<String>()
        var current by mutableStateOf(Case(languages.first(), 360, 2f))
        var page by mutableStateOf("Discover")
        var settingsPrefs by mutableStateOf(emptyPreferences())
        var keyboardMode: () -> String = { "host not ready" }
        val fixture = ImportedRoute("layout-fixture", "QA GPX 51.5000, -0.1000", "51.5,-0.1;51.501,-0.099", 120.0, 0L, "fixture", false)
        val walks = listOf(Walk("a11y-a","QA walk A",System.currentTimeMillis(),120.0,20,"",3,true),Walk("a11y-b","QA walk B",System.currentTimeMillis(),240.0,30,"",0,false))
        val photos = listOf(WalkPhoto("qa-photo-1","a11y-a","qa-missing-1.jpg"),WalkPhoto("qa-photo-2","a11y-a","qa-missing-2.jpg"))
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
            val scene: @Composable () -> Unit = {
                val input = LocalInputModeManager.current
                val view = LocalView.current
                SideEffect { keyboardMode = { val mode = input.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard); val focused = view.requestFocus(); "keyboard=$mode viewFocus=$focused window=${view.hasWindowFocus()} touch=${view.isInTouchMode}" } }
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                LocalDensity provides Density(density, current.scale),
                LocalLayoutDirection provides if (current.tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalTextLayoutAudit provides { text, result -> if (result.hasVisualOverflow) overflows.add(text.take(120)) }) {
                MaterialTheme {
                    Box(Modifier.requiredSize(current.width.dp, config.screenHeightDp.dp).testTag("layout-viewport").semantics { testTagsAsResourceId = true }) {
                        key(current) {
                            TrailScaffold(page, { page = it }) {
                                when (current.screen) {
                                    "Discover" -> DiscoverScreen({}, {})
                                    "My walks" -> JournalScreen(walks, photos, false, false, {}, {}, { _, _ -> }, {}, recording = { RecordingPanel(RecordingDraft("fixture", 0, 61_000, 120.0), false, false, {}, { _, _ -> }) })
                                    "Checklist" -> ChecklistScreen(PersonalChecklist.initial().mapIndexed { i, item -> item.copy(title = context.checklistDefault(i)) }, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, {})
                                    "Offline packs" -> PacksScreen(listOf(fixture), false, {}, {}, {}, {})
                                    "Settings" -> SettingsScreen(settingsPrefs, { key -> settingsPrefs = settingsPrefs.toMutablePreferences().apply { this[booleanPreferencesKey(key)] = !(this[booleanPreferencesKey(key)] ?: false) } }, { key,value -> settingsPrefs = settingsPrefs.toMutablePreferences().apply { this[stringPreferencesKey(key)] = value } }, false)
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
            val checks = JSONArray()
            try {
                val strings = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
                compose.runOnIdle { overflows.clear(); settingsPrefs = emptyPreferences(); page = "Discover"; current = Case(tag, width, scale) }
                compose.waitForIdle()
                compose.onNodeWithTag("layout-viewport").assertWidthIsEqualTo(width.dp).assertHeightIsEqualTo(640.dp)
                if (compose.onAllNodesWithTag("navigation-toggle").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("navigation-toggle").performClick()
                compose.onNodeWithTag("nav-Discover").performScrollTo().assertIsDisplayed()
                val icon = compose.onNodeWithTag("nav-icon-Discover", true).fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("nav-label-Discover", true).fetchSemanticsNode().boundsInRoot
                Assert.assertTrue("Navigation direction $id", if (tag == "ar") icon.center.x > label.center.x else icon.center.x < label.center.x)
                val navigation = runCatching { compose.auditNavigationFocus(listOf(R.string.discover,R.string.my_walks,R.string.offline,R.string.checklist,R.string.settings).map { strings.getString(it) },keyboardMode) }.getOrElse { error ->
                    failures += "$id/Navigation focus: ${error.message}"
                    JSONObject().put("error",error.message)
                }
                checks.put(compose.auditAccessibleControls(app.resources.displayMetrics.density).put("screen","Navigation"))
                if (compose.onAllNodesWithTag("navigation-toggle").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithTag("nav-Settings").performKeyInput { pressKey(Key.Escape) }
                    compose.onNodeWithTag("navigation-toggle").assertIsFocused().performClick()
                }
                compose.onNodeWithTag("nav-Settings").performScrollTo().assertIsDisplayed().performClick()
                accessibility.put(JSONObject().put("configuration",id).put("navigation",navigation).put("controls",checks))
                for (screen in screens) {
                    try {
                    compose.runOnIdle { current = Case(tag, width, scale, screen); page = if (screen in screens.take(5)) screen else "Offline packs" }
                    compose.waitForIdle()
                    if (screen == "My walks") {
                        compose.onNodeWithTag("journal-list").performScrollToNode(hasText(strings.getString(R.string.record_walk)))
                        compose.onNodeWithText(strings.getString(R.string.record_walk)).performClick()
                        compose.onNodeWithTag("journal-list").performScrollToNode(hasText(strings.getString(R.string.save_walk)))
                        compose.onNodeWithText(strings.getString(R.string.save_walk)).performSemanticsAction(SemanticsActions.OnClick) { it() }
                        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion,LiveRegionMode.Polite))
                        checks.put(compose.auditAccessibleControls(app.resources.displayMetrics.density).put("screen","Journal editor"))
                        for (walk in walks) {
                            val editDescription = strings.getString(R.string.accessibility_action_for,strings.getString(R.string.edit_walk),walk.title)
                            compose.onNodeWithTag("journal-list").performScrollToNode(hasContentDescription(editDescription))
                            checks.put(compose.auditAccessibleControls(app.resources.displayMetrics.density).put("screen","Journal card ${walk.id}"))
                            compose.onNodeWithContentDescription(editDescription).assertIsDisplayed()
                            compose.onNodeWithContentDescription(strings.getString(R.string.accessibility_rating,3,walk.title)).assertExists()
                        }
                    }
                    if (screen == "Checklist") {
                        compose.onNodeWithText(strings.getString(R.string.edit_checklist)).performScrollTo().performClick()
                        val subject = strings.checklistDefault(0)
                        compose.onNodeWithContentDescription(strings.getString(R.string.accessibility_action_for,strings.getString(R.string.rename_item),subject)).assertExists()
                    }
                    if (screen == "Settings") {
                        compose.onNodeWithTag("setting-dark").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Switch)).assert(hasText(strings.getString(R.string.ui_dark_appearance))).assertIsOff().performClick().assertIsOn()
                        compose.onNodeWithText(strings.getString(R.string.ui_kilometres)).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.RadioButton)).performClick().assertIsSelected()
                        compose.onNodeWithText(strings.getString(R.string.ui_miles)).assertIsNotSelected()
                    }
                    if (screen == "Recording") {
                        compose.onNodeWithText(strings.getString(R.string.finish_recording)).performScrollTo().performClick()
                        compose.onNodeWithText(strings.getString(R.string.save_walk)).performScrollTo().assertExists()
                    }
                    if (screen == "Share") {
                        compose.onNodeWithText(strings.getString(R.string.share_plan_title)).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText(strings.getString(R.string.expected_return)).performScrollTo().assertIsDisplayed()
                        compose.onNodeWithText(strings.getString(R.string.open_share_options)).performScrollTo().assertIsDisplayed()
                    }
                    if (screen == "Route") compose.onNodeWithContentDescription(strings.getString(R.string.accessibility_geometry,fixture.title)).assertExists()
                    Assert.assertTrue("No accessible heading on $screen",compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes().isNotEmpty())
                    checks.put(compose.auditAccessibleControls(app.resources.displayMetrics.density).put("screen",screen))
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
                    } catch (error: Throwable) {
                        failures += "$id/$screen: ${error.message}"
                        checks.put(JSONObject().put("screen",screen).put("error",error.message))
                        overflows.clear()
                    }
                }
                results.put(JSONObject().put("locale",tag).put("widthDp",width).put("heightDp",640).put("fontScale",scale).put("screens",JSONArray(screens)).put("status",if(failures.any { it.startsWith(id) }) "failed" else "passed"))
            } catch (error: Throwable) {
                failures += "$id/${current.screen}: ${error.message} at ${error.stackTrace.firstOrNull { it.className == javaClass.name }}"
                results.put(JSONObject().put("locale",tag).put("widthDp",width).put("fontScale",scale).put("screen",current.screen).put("status","failed").put("error",error.message))
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(output,"$id-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
            }
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "Layout matrix: $id complete\n") })
            File(output,"matrix.json").writeText(JSONObject().put("configurations",results).put("accessibility",accessibility).put("failures",JSONArray(failures)).put("device",android.os.Build.MODEL).put("api",android.os.Build.VERSION.SDK_INT).toString(2))
        }
        Assert.assertTrue(failures.take(12).joinToString("\n"), failures.isEmpty())
    }
}
