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

/** Explicit provider acceptance on the isolated draft package only. The test
 * calls the downloader directly; it does not grant or fake a paid entitlement.
 * Download once online, then rerun with phase=offline after network removal
 * and a cold process restart. Leave the installed LAT package alone. */
class MapProviderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun providerMapDownloadAndRender() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("mapProviderAcceptance") == "true")
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val route = RouteCatalogue.load(context).first().route
        val key = "TNMAP1:${route.sha256}"
        lateinit var offline: OfflineMaps
        compose.runOnUiThread { offline = OfflineMaps.get(context) }
        compose.waitUntil(10000) { offline.ready.value }
        val phase = arguments.getString("mapPhase") ?: "download"
        if (phase == "download") {
            compose.runOnUiThread { offline.download(key, Gpx.points(route)) }
            compose.waitUntil(180000) { offline.packs.value[key]?.state in listOf(MapPackState.COMPLETE, MapPackState.FAILED, MapPackState.LIMIT) }
            assertEquals(MapPackState.COMPLETE, offline.packs.value[key]?.state)
        } else {
            val connectivity = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            assertNull("Disconnect networking before the cold offline acceptance phase", connectivity.activeNetwork)
            compose.waitUntil(10000) { offline.packs.value.containsKey(key) }
            assertEquals("Offline phase requires the online download to have completed in the earlier process", MapPackState.COMPLETE, offline.packs.value[key]?.state)
        }
        compose.onAllNodesWithText("Open route").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Load map").performScrollTo().performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("route-map-rendered").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("route-map-rendered").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Offline map ready", substring = true).performScrollTo().assertIsDisplayed()
    }
}
