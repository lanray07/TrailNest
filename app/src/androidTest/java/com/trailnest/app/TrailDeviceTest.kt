package com.trailnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trailnest.app.data.Gpx
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.trailnest.app.data.TrailDatabase
import com.trailnest.app.data.Walk
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrailDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var originalPreferences: Preferences? = null
    @Before fun isolateChecklist() = runBlocking {
        prepareTestWindow { compose.activity }
        val store = InstrumentationRegistry.getInstrumentation().targetContext.preferences
        originalPreferences = store.data.first()
        store.edit { prefs -> prefs.remove(stringPreferencesKey("personal_checklist_v1")); (0..10).forEach { prefs.remove(booleanPreferencesKey("kit_$it")) } }
        Unit
    }
    @After fun restorePreferences() = runBlocking {
        originalPreferences?.let { original -> InstrumentationRegistry.getInstrumentation().targetContext.preferences.updateData { original } }
        Unit
    }
    @Test fun homeExplainsUnavailableCoverage() {
        compose.onNodeWithText("Good walks start with good information.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Explore coverage").performScrollTo().performClick()
        compose.onNodeWithText("Good information comes first.").assertIsDisplayed()
    }
    @Test fun checklistCanBeChecked() {
        compose.onNodeWithText("Checklist", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Reset checklist").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("0 of 11 items checked").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Drinking water").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn()
    }
    @Test fun customChecklistItemCanBeAdded() {
        compose.onNodeWithText("Checklist", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Edit checklist").performClick()
        compose.onNodeWithText("Item name").performScrollTo().performTextInput("QA binoculars")
        compose.onNodeWithText("Add item").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("QA binoculars").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("QA binoculars").performScrollTo().assertIsDisplayed()
    }
    @Test fun journalEditsPreserveRatingFavouriteAndExactDistance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = TrailDatabase.create(context).dao()
        val id = UUID.randomUUID().toString()
        val walk = Walk(id, "QA journal edit fixture", System.currentTimeMillis(), 1234.567891, 25, "Original QA notes", 4, true)
        runBlocking { dao.save(walk) }
        try {
            compose.onNodeWithText("My walks", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("journal-list").performScrollToNode(hasTestTag("walk-$id"))
            compose.onNode(hasText("Edit walk") and hasAnyAncestor(hasTestTag("walk-$id"))).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("journal-editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Notes").performTextReplacement("Edited QA notes")
            val day = LocalDate.now().minusDays(1)
            compose.onNodeWithText("Walk date").performTextReplacement(day.toString())
            compose.onNodeWithTag("journal-list").performScrollToNode(hasText("Save walk"))
            compose.onNodeWithText("Save walk").performClick()
            compose.waitUntil(5000) { runBlocking { dao.walk(id)?.notes == "Edited QA notes" } }
            val edited = runBlocking { dao.walk(id) }!!
            assertEquals(walk.metres, edited.metres, 0.0); assertEquals(4, edited.rating); assertTrue(edited.favourite)
            assertEquals(day, java.time.Instant.ofEpochMilli(edited.date).atZone(ZoneId.systemDefault()).toLocalDate())
        } finally { runBlocking { dao.deleteWalk(id) } }
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
