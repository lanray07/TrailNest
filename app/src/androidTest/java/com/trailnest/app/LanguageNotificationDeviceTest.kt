package com.trailnest.app

import android.content.Intent
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import java.util.Locale

/** Notification navigation uses Android's real clock, not Compose's controlled test clock. */
class LanguageNotificationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun text(id: Int, tag: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).getString(id)

    private fun findVisible(value: String): AccessibilityNodeInfo? {
        // Traverse Compose's virtual descendants: provider text-search is not
        // equivalent to walking the exported accessibility tree on older Fire OS.
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isVisibleToUser && node.packageName == context.packageName &&
                (node.text?.contains(value) == true || node.contentDescription?.contains(value) == true)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> visit(child)?.let { return it } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let { visit(it) }
    }

    private fun awaitVisible(value: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (findVisible(value) != null) return
            SystemClock.sleep(50)
        }
        Assert.fail("Expected app text: $value")
    }
    private fun click(value: String) {
        awaitVisible(value)
        var node = findVisible(value)
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        Assert.fail("No clickable app control: $value")
    }

    @Test fun notificationNavigationIsNotReplayedByLanguageRecreation() {
        Assume.assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        var original = ""
        val intent = Intent(context, MainActivity::class.java).setAction(WalkRecordingService.OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            try {
                // Cold notification opens recording without mutating its launch intent.
                scenario.onActivity {
                    original = AppLanguage.selectedTag()
                    Assert.assertEquals(WalkRecordingService.OPEN, it.intent.action)
                }
                val startingTag = original.ifEmpty { context.resources.configuration.locales[0].toLanguageTag() }
                awaitVisible(text(R.string.recording_channel, startingTag))
                click(text(R.string.settings, startingTag))
                click(text(R.string.language_title, startingTag))
                click(AppLanguage.nativeName("fr"))
                awaitVisible(text(R.string.ui_your_preferences, "fr"))
                scenario.onActivity {
                    Assert.assertEquals("fr", it.resources.configuration.locales[0].toLanguageTag())
                    Assert.assertEquals(WalkRecordingService.OPEN, it.intent.action)
                }
                // Same flags as the recording notification's PendingIntent; deliver warm.
                val command = "am start -n ${context.packageName}/com.trailnest.app.MainActivity -a ${WalkRecordingService.OPEN} -f 0x24000000"
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
                awaitVisible(text(R.string.recording_channel, "fr"))
                click(text(R.string.settings, "fr"))
                click(text(R.string.language_title, "fr"))
                click(AppLanguage.nativeName("de"))
                awaitVisible(text(R.string.ui_your_preferences, "de"))
                scenario.onActivity {
                    Assert.assertEquals("de", it.resources.configuration.locales[0].toLanguageTag())
                    Assert.assertEquals(WalkRecordingService.OPEN, it.intent.action)
                }
            } finally {
                scenario.onActivity { AppLanguage.select(it, original) }
                instrumentation.waitForIdleSync()
            }
        }
    }
}
