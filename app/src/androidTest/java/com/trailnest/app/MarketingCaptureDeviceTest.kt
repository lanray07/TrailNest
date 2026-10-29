package com.trailnest.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/** Export real production composables with isolated, explicitly illustrative data.
 * No user DB, Amazon app, purchase, device locale or device font setting is changed.
 */
class MarketingCaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private data class Scene(val locale: String, val screen: String)

    @Test fun exportLocalizedTabletScreenshots() {
        Assume.assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val copy = instrumentation.context.assets.open("marketing/locales.json").bufferedReader().use { JSONObject(it.readText()) }
        val requested = InstrumentationRegistry.getArguments().getString("marketingLocales")
        val tags = requested?.split(',') ?: AppLanguage.supportedTags(app)
        val screens = listOf("journal", "recording", "checklist", "photos", "gpx", "progress", "sharing", "settings")
        val output = File(app.getExternalFilesDir(null), "marketing-captures").apply { mkdirs() }
        val results = JSONArray()
        val overflows = ConcurrentLinkedQueue<String>()
        val failures = mutableListOf<String>()
        var scene by mutableStateOf(Scene(tags.first(), "journal"))
        val geometry = "51.5000,-0.1000;51.5010,-0.0995;51.5015,-0.0980;51.5008,-0.0970;51.4998,-0.0980"
        val photoStore = PhotoStore(app)
        val photo = photoStore.import(Uri.parse("android.resource://${app.packageName}/${R.drawable.landscape}"))
        instrumentation.runOnMainSync {
            @Suppress("DEPRECATION")
            compose.activity.window.decorView.systemUiVisibility = 0
        }
        try {
            compose.setContent {
                val host = LocalContext.current
                val hostConfig = LocalConfiguration.current
                val config = remember(hostConfig, scene.locale) {
                    Configuration(hostConfig).apply { setLocale(Locale.forLanguageTag(scene.locale)); setLayoutDirection(Locale.forLanguageTag(scene.locale)); fontScale = 1f }
                }
                val context = remember(config) { android.view.ContextThemeWrapper(host, R.style.Theme_TrailNest).apply { applyOverrideConfiguration(config) } }
                val strings = copy.getJSONObject(scene.locale)
                val route = ImportedRoute("marketing-route", context.getString(R.string.imported_walk), geometry, 490.0, 0L, Gpx.digest(geometry), true)
                val walk = Walk("marketing-walk", strings.getString("sampleWalk"), Instant.parse("2026-10-28T09:00:00Z").toEpochMilli(), 1800.0, 35, "", 4, true)
                val page = when (scene.screen) { "journal", "recording", "photos" -> "My walks"; "checklist" -> "Checklist"; "settings" -> "Settings"; else -> "Offline packs" }
                val content: @Composable () -> Unit = {
                    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                        LocalLayoutDirection provides if (scene.locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                        LocalTextLayoutAudit provides { text, result -> if (result.hasVisualOverflow) overflows.add(text.take(120)) }) {
                        MaterialTheme(colorScheme = trailColorScheme(false)) {
                            Surface(Modifier.fillMaxSize(), color = Ivory) {
                                Column(Modifier.fillMaxSize()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Image(painterResource(R.drawable.ic_trailnest), null, Modifier.size(52.dp))
                                        Column(Modifier.weight(1f)) {
                                            TrailText("TrailNest", color = Forest, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                                            TrailText(strings.getJSONObject("headlines").getString(scene.screen), color = Ink, fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 46.sp)
                                        }
                                    }
                                    HorizontalDivider(color = Forest.copy(alpha = 0.2f))
                                    Box(Modifier.weight(1f)) {
                                        key(scene) {
                                            TrailScaffold(page, {}) {
                                                when (scene.screen) {
                                                    "journal", "photos" -> JournalScreen(listOf(walk), if (scene.screen == "photos") listOf(WalkPhoto("marketing-photo", walk.id, photo)) else emptyList(), false, false, {}, {}, { _, _ -> }, {})
                                                    "recording" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) { RecordingPanel(RecordingDraft("marketing", 0L, 35 * 60_000L, 1800.0), false, false, {}, { _, _ -> }) }
                                                    "checklist" -> ChecklistScreen(PersonalChecklist.initial().mapIndexed { index, item -> item.copy(title = context.checklistDefault(index), checked = index < 3) }, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}, {})
                                                    "gpx" -> PacksScreen(listOf(route), false, {}, {}, {}, {})
                                                    "progress" -> RouteScreen(route, null, false, false, false, VoiceCommand.UNKNOWN, {}, {}, {}, {}, {}, {}, {}, {})
                                                    "sharing" -> { PacksScreen(listOf(route), false, {}, {}, {}, {}); SharePlanDialog(route, false, {}) {} }
                                                    "settings" -> SettingsScreen(preferencesOf(stringPreferencesKey("units") to "metric"), {}, { _, _ -> }, false)
                                                }
                                            }
                                        }
                                    }
                                    TrailText(strings.getString("demoLabel"), Modifier.fillMaxWidth().background(Sage).padding(horizontal = 28.dp, vertical = 8.dp), color = Forest, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                key(config) { AndroidView(factory = { ComposeView(context).apply { setContent(content) } }, modifier = Modifier.fillMaxSize()) }
            }
            for (tag in tags) for (screen in screens) {
                val expected = copy.getJSONObject(tag).getJSONObject("expectedStrings")
                if (screen == screens.first()) {
                    val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
                    for (key in expected.keys()) {
                        val id = resources.resources.getIdentifier(key, "string", app.packageName)
                        Assert.assertTrue("Missing installed resource $tag/$key", id != 0)
                        fun normalized(value: String) = value.trim().replace(Regex("\\s+"), " ")
                        Assert.assertEquals("Installed translation differs: $tag/$key", normalized(expected.getString(key)), normalized(resources.getString(id)))
                    }
                }
                compose.runOnIdle { overflows.clear(); scene = Scene(tag, screen) }
                compose.waitForIdle()
                if (screen == "photos") {
                    compose.onNodeWithTag("walk-marketing-walk").performScrollTo()
                    compose.waitForIdle()
                    Thread.sleep(300)
                    compose.onNodeWithContentDescription(app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).getString(R.string.walk_photo_description, 1, copy.getJSONObject(tag).getString("sampleWalk"))).assertExists()
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(250)
                val errors = overflows.toSet()
                if (errors.isNotEmpty()) failures += "$tag/$screen: ${errors.joinToString()}"
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                Assert.assertNotNull("Screenshot unavailable for $tag/$screen", bitmap)
                val directory = File(output, tag).apply { mkdirs() }
                val file = File(directory, "$screen.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                results.put(JSONObject().put("locale", tag).put("uiLocale", tag).put("screenId", screen).put("version", BuildConfig.VERSION_NAME.removeSuffix("-draft"))
                    .put("file", "$tag/$screen.png").put("sha256", sha).put("width", bitmap.width).put("height", bitmap.height)
                    .put("headline", copy.getJSONObject(tag).getJSONObject("headlines").getString(screen)).put("overflowCount", errors.size)
                    .put("contentFingerprint", copy.getJSONObject(tag).getString("contentFingerprint")).put("resourceStringsVerified", expected.length()))
                bitmap.recycle()
                File(output, "captures.json").writeText(JSONObject().put("captures", results).put("failures", JSONArray(failures)).put("device", android.os.Build.MODEL)
                    .put("source", "Real production composables; synthetic example data; original app landscape used as illustrative private photo; no GPS/purchase claims")
                    .put("package", BuildConfig.APPLICATION_ID).toString(2))
                instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "Marketing capture: $tag/$screen complete\n") })
            }
            Assert.assertTrue(failures.take(12).joinToString("\n"), failures.isEmpty())
        } finally { photoStore.remove(photo) }
    }
}
