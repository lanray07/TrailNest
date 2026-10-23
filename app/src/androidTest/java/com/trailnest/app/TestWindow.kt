package com.trailnest.app

import android.app.Activity
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry

/** Only this synthetic test window is shown; the device remains PIN-locked. */
fun prepareTestWindow(activity: () -> Activity) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    // Fire OS may recreate an activity when it first occludes its landscape lock screen.
    repeat(2) {
        val current = activity()
        instrumentation.runOnMainSync {
            if (BuildConfig.DRAFT_TRANSLATIONS && Build.VERSION.SDK_INT >= 27) { current.setTurnScreenOn(true); current.setShowWhenLocked(true) }
            current.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
    }
    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 224")).use { it.readBytes() }
    instrumentation.waitForIdleSync()
}
