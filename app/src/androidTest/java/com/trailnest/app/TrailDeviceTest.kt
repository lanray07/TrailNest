package com.trailnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trailnest.app.data.Gpx
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeExplainsUnavailableCoverage() {
        compose.onNodeWithText("Good walks start with good information.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Explore coverage").performClick()
        compose.onNodeWithText("Good information comes first.").assertIsDisplayed()
    }
    @Test fun checklistCanBeChecked() {
        compose.onNodeWithText("Checklist", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Drinking water").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
    }
    @Test fun gpxRejectsUnsafeAndDisconnectedData() {
        val valid = "<gpx><trk><name>TEST FIXTURE ONLY</name><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0' lon='0.001'/></trkseg></trk></gpx>"
        val route = Gpx.read(valid.byteInputStream(), "fixture")
        assertEquals(2, Gpx.points(route).size)
        assertEquals(Gpx.digest(route.geometry), route.sha256)
        val roundtrip = Gpx.read(Gpx.export(route).byteInputStream(), "fixture")
        assertEquals(route.geometry, roundtrip.geometry)
        for (invalid in listOf("<!DOCTYPE gpx [<!ENTITY x 'bad'>]><gpx/>", valid.replace("</trk>", "<trkseg><trkpt lat='0' lon='1'/></trkseg></trk>"), valid.replace("lat='0'", "lat='91'"))) {
            assertTrue(runCatching { Gpx.read(invalid.byteInputStream(), "fixture") }.isFailure)
        }
    }
}
