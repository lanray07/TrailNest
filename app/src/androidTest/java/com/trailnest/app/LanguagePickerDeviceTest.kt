package com.trailnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*

/** Exercise actual activity recreation and AndroidX locale storage on older Fire OS. */
class LanguagePickerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allConfiguredLanguagesApplyAndDeviceDefaultCanBeRestored() {
        Assume.assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val original = AppLanguage.selectedTag()
        val tags = AppLanguage.supportedTags(compose.activity)
        Assert.assertEquals(19, tags.distinct().size)
        try {
            // A previously consumed recording notification must not reopen My walks
            // when a later language change recreates Settings.
            compose.runOnIdle {
                val activity = compose.activity
                activity.startActivity(android.content.Intent(activity, MainActivity::class.java)
                    .setAction(WalkRecordingService.OPEN).addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-recording").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("navigation-toggle").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("navigation-toggle").performClick()
            compose.onNodeWithTag("nav-Settings").performScrollTo().performClick()
            for (tag in tags) {
                compose.onNodeWithTag("language-picker").performScrollTo().performClick()
                compose.onNodeWithTag("language-option-$tag").performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(10_000) { compose.activity.resources.configuration.locales[0].toLanguageTag() == tag }
                compose.waitForIdle()
                Assert.assertEquals(tag, AppLanguage.selectedTag())
                val activity = compose.activity
                Assert.assertEquals(activity.getString(R.string.language_title), ContextCompat.getString(activity.applicationContext, R.string.language_title))
                compose.onNodeWithTag("language-picker").performScrollTo().assert(hasText(AppLanguage.nativeName(tag)))
                Assert.assertEquals(if (tag == "ar") android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR,
                    activity.resources.configuration.layoutDirection)
                // The selected Settings page survives locale-triggered recreation.
                compose.onNodeWithText(activity.getString(R.string.ui_your_preferences)).assertExists()
            }
            // Persist a regional/script tag and recreate through the platform as well.
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            Assert.assertEquals(tags.last(), compose.activity.resources.configuration.locales[0].toLanguageTag())
            compose.onNodeWithTag("language-picker").performScrollTo().performClick()
            compose.onNodeWithTag("language-option-device").performScrollTo().performClick()
            compose.waitUntil(10_000) { AppLanguage.selectedTag().isEmpty() }
            compose.waitForIdle()
            compose.onNodeWithTag("language-picker").performScrollTo()
                .assert(hasText(compose.activity.getString(R.string.language_device)))
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { AppLanguage.select(compose.activity, original) }
        }
    }
}
