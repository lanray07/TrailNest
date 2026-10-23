# Share dialog and software keyboard

The share form uses a dialog window with explicit system/keyboard insets and resize handling. Its surface is bounded by both the configured pane size and the actual space above the keyboard. The whole form scrolls, including the title and action row, so large headings and wrapped actions cannot consume a fixed body slot. Scrolling to an action should leave its full touch target above the keyboard. With the keyboard open, the first Back hides it; the next Back dismisses the dialog. Outside taps and Cancel dismiss the dialog; the keyboard's Done action clears input focus without sharing.

Duration, return time and contact fields are single-line inputs. Duration requests a numeric keyboard. Next moves from duration to return time to contact; Done hides the keyboard. ISO return timestamps retain left-to-right input direction in Arabic. Validation and explicit share callbacks are unchanged; entering text never sends a plan automatically.

## Device verification

Build the isolated preview app and instrumented tests using the commands in `VERIFICATION.md`. Run `com.trailnest.app.ShareKeyboardDeviceTest` in `com.trailnest.app.draft.test/androidx.test.runner.AndroidJUnitRunner`. The default matrix covers all 19 locales, 360/800 dp pane widths and 100%/200% fonts. `-e layoutSmoke true` selects Arabic/German at 200%.

The test requires a real visible input-method window from Android UiAutomation. It scrolls actions into view, reads their native screen bounds and checks that the full target is above the keyboard, then injects touchscreen events at those screen coordinates. Callback/validation assertions establish that those touches reached the form. It checks invalid duration, successful plan creation retaining an entered contact, cancellation and Next-key focus order. No system Sharesheet or contact receives a message; the callback captures text only in memory.

The Fire tablet remains PIN-locked. Instrumentation temporarily marks only synthetic draft dialog windows to show above the lock, allowing their keyboard to appear. Normal app windows receive no lock-screen flags. Device locale, font settings and normal app data are untouched. The matrix tests 19 app-language layouts with the existing Fire keyboard (`com.amazon.redstone/.FireKeyboardService`) and en-GB system locale; it does not switch the keyboard language. Other keyboards/devices and human VoiceView/TalkBack and linguistic review remain acceptance work. Every translation remains a draft.

The earlier 0.6 screenshots and report remain historical evidence of the keyboard occlusion. See `VERIFICATION.md` and the versioned artifacts for the results actually executed against this version.

Implementation follows Android's [dialog window-inset properties](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties) and [Compose inset handling](https://developer.android.com/develop/ui/compose/system/insets-ui).
