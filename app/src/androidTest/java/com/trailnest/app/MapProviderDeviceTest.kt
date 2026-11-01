package com.trailnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.data.RouteCatalogue
import com.trailnest.app.data.Gpx
import com.trailnest.app.maps.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.After

/** Explicit provider acceptance on the isolated draft package only. The test
 * calls the downloader directly; it does not grant or fake a paid entitlement.
 * Download once online, then rerun with phase=offline after network removal
 * and a cold process restart. Leave the installed LAT package alone. */
class MapProviderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun restoreRequestedNetworkState() {
        if (InstrumentationRegistry.getArguments().getString("mapProviderAcceptance") != "true") return
        if (InstrumentationRegistry.getArguments().getString("restoreWifiAfter") == "true") {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("svc wifi enable")).use { it.readBytes() }
        }
    }
    @Test fun providerMapDownloadAndRender() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("mapProviderAcceptance") == "true")
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val phase = arguments.getString("mapPhase") ?: "download"
        require(phase in listOf("download", "offline", "interrupt"))
        val routeIndex = if (phase == "interrupt") 1 else 0
        val route = RouteCatalogue.load(context)[routeIndex].route
        val key = "TNMAP1:${route.sha256}"
        lateinit var offline: OfflineMaps
        compose.runOnUiThread { offline = OfflineMaps.get(context) }
        compose.waitUntil(10000) { offline.ready.value }
        fun clearAmbientCache() {
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val success = java.util.concurrent.atomic.AtomicBoolean(false)
            compose.runOnUiThread {
                org.maplibre.android.offline.OfflineManager.getInstance(context).clearAmbientCache(object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                    override fun onSuccess() { success.set(true); done.set(true) }
                    override fun onError(message: String) { done.set(true) }
                })
            }
            compose.waitUntil(10000) { done.get() }; assertTrue("Ambient test-cache cleanup failed", success.get())
        }
        if (phase != "offline") {
            val deleted = java.util.concurrent.atomic.AtomicBoolean(false)
            val success = java.util.concurrent.atomic.AtomicBoolean(false)
            compose.runOnUiThread { offline.delete(key) { result -> success.set(result); deleted.set(true) } }
            compose.waitUntil(10000) { deleted.get() }; assertTrue("Prior test region deletion failed", success.get())
            if (phase == "download") clearAmbientCache()
            compose.runOnUiThread {
                offline.download(key, Gpx.points(route))
                if (phase == "interrupt") { offline.pause(key); offline.download(key, Gpx.points(route)) }
            }
            compose.waitUntil(180000) { offline.packs.value[key]?.state in listOf(MapPackState.COMPLETE, MapPackState.FAILED, MapPackState.LIMIT) }
            assertEquals("budget bytes=${MapNetwork.budget?.bytes}, resources=${MapNetwork.budget?.resources}; pack=${offline.packs.value[key]}", MapPackState.COMPLETE, offline.packs.value[key]?.state)
            if (phase == "interrupt") {
                val latch = java.util.concurrent.CountDownLatch(1)
                val count = java.util.concurrent.atomic.AtomicInteger(-1)
                compose.runOnUiThread {
                    org.maplibre.android.offline.OfflineManager.getInstance(context).listOfflineRegions(object : org.maplibre.android.offline.OfflineManager.ListOfflineRegionsCallback {
                        override fun onList(regions: Array<org.maplibre.android.offline.OfflineRegion>?) { count.set(regions.orEmpty().count { it.metadata.toString(Charsets.UTF_8) == key }); latch.countDown() }
                        override fun onError(error: String) { latch.countDown() }
                    })
                }
                assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)); assertEquals("Interrupted create/resume left duplicate native regions", 1, count.get())
            }
        } else {
            val connectivity = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            assertNull("Disconnect networking before the cold offline acceptance phase", connectivity.activeNetwork)
            compose.waitUntil(10000) { offline.packs.value.containsKey(key) }
            assertEquals("Offline phase requires the online download to have completed in the earlier process", MapPackState.COMPLETE, offline.packs.value[key]?.state)
            // Clear again while disconnected: an earlier test failure may have
            // restored networking before its activity finished shutting down.
            clearAmbientCache()
        }
        compose.onAllNodesWithText("Open route")[routeIndex].performScrollTo().performClick()
        compose.onNodeWithText("Load map").performScrollTo().performClick()
        // A connectivity configuration update can briefly remove the activity's
        // root; the final rendered-map assertion remains mandatory.
        compose.waitUntil(60000) { runCatching { compose.onAllNodesWithTag("route-map-rendered").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNodeWithTag("route-map-rendered").performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Map screenshot unavailable", screenshot)
        java.io.File(context.getExternalFilesDir(null), "map-$phase.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        val pack = offline.packs.value[key]!!
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Map phase=$phase complete; tiles=${pack.tilesSaved}; bytes=${pack.bytesSaved}; resources=${pack.completed}/${pack.required}\n") })
        compose.onNodeWithText("Offline map ready", substring = true).performScrollTo().assertIsDisplayed()
        if (phase != "offline") {
            // Remove online-view cache after destroying MapView. The next cold,
            // disconnected phase must render from the pinned region itself.
            compose.onNodeWithText("Back to routes").performScrollTo().performClick()
            clearAmbientCache()
        }
    }
}
