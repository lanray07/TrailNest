package com.trailnest.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trailnest.app.domain.RecordingDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingUiDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun finishPausesBeforeReviewAndSavesEnteredTitle() {
        var draft by mutableStateOf<RecordingDraft?>(RecordingDraft("test", 123, 61_000, 15.0, running = true))
        var saved = ""
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { RecordingPanel(draft, false, false,
            { action -> if (action == "pause") draft = draft!!.copy(running = false) },
            { title, _ -> saved = title }) } } }
        compose.onNodeWithText("Finish and review").performClick()
        compose.onNodeWithText("Walk recording paused").assertExists()
        compose.onNodeWithText("Save walk").assertIsNotEnabled()
        compose.onNodeWithText("Walk title").performTextInput("Test recorded walk")
        compose.onNodeWithText("Save walk").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Test recorded walk", saved); assertFalse(draft!!.running) }
    }
}
