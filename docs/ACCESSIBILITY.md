# Android accessibility checks

The native app uses Material control semantics, localised visible labels and additional descriptions where an action needs context. Repeated checklist, journal, photo and import actions identify their target. Rating descriptions name the score out of five and the walk. User-entered names remain literal; translations remain unreviewed drafts.

Headings expose heading semantics. Pages expose pane titles; navigation and page content have separate traversal groups, and navigation destinations have explicit reading-order indices. The compact menu supports keyboard focus entry, Escape/Back dismissal and returning focus to its toggle. Keyboard input focus and accessibility focus are checked separately.

The dark-appearance setting is one labelled switch row; its drawn switch does not introduce another toggle action. Distance choices expose a single-choice group and radio-button roles. Checklist rows expose one labelled checkbox. Validation messages expose error semantics and a polite live region; recording timers do not announce every update. The route geometry overview has a textual description.

## Repeat the checks

Run the normal builds, JVM tests and lint commands in `VERIFICATION.md`. Generate and validate preview resources with `node tools/localise.mjs --check --preview`, then build the isolated draft app and tests:

```powershell
.\gradlew.bat :app:assembleTranslationPreview :app:assembleTranslationPreviewAndroidTest -PtrailnestTestBuildType=translationPreview --offline --max-workers=2
```

Install the `translationPreview` app and its `androidTest/translationPreview` APK, then run `com.trailnest.app.LocaleLayoutDeviceTest` with `androidx.test.runner.AndroidJUnitRunner` in `com.trailnest.app.draft.test`. The matrix uses 19 locales, 360×640/800×640 dp panes and 100%/200% fonts. Add `-e layoutSmoke true` for the Arabic/German 200% subset.

The test checks accessible action names, control roles/states, headings, contextual labels, error announcements, target dimensions, keyboard navigation and exported Android accessibility focus/traversal. It includes populated journal cards, missing-photo fixtures, checklist editing, settings, route geometry, sharing and recording review. It uses synthetic fixtures and the separate draft package; normal app data and system locale/font settings are untouched. Android hosting contexts also configure separate dialog windows.

Target measurements use placed layout allocations, including Material's reserved interaction space. Prefetched lazy-list items that have not been placed can expose zero-size semantics; the report counts them separately and excludes them from size measurements. Journal edit actions are scrolled into view and asserted visible before their card audit. Actual hit bounds are additionally checked for fully visible semantic targets measuring at least 48 dp; scroll-edge clipping is excluded from that check.

The UiAutomation service temporarily requests accessibility-tree information and touch exploration to exercise accessibility focus; its service flags are restored. It does not enable VoiceView or another installed screen reader. Draft test windows can display above the PIN lock without unlocking the device. Those window flags end with their activities.

## Human acceptance remains required

See `VERIFICATION.md` and the versioned artifacts for executed results. Automated semantics, native node and focus checks do not prove spoken pronunciation, announcement quality, reader-specific swipe behaviour, gesture usability or translation accuracy. A human should review all drafts with VoiceView/TalkBack, including grouped controls, error announcements, dialog entry/exit, reading order, RTL mixed-script names and alternative input. Other hardware and Android versions still need acceptance testing. These checks do not approve the release-evidence accessibility or translation flags.

Compose visibility assertions do not detect every separate-window occlusion. The saved share-dialog captures include the software keyboard covering the lower dialog area; they do not demonstrate access to its buttons while that keyboard is open. Keyboard-open dialog resizing, scrolling and dismissal need a separate device acceptance pass.

Reference guidance: [Android semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [traversal groups and indices](https://developer.android.com/develop/ui/compose/accessibility/traversal), and [default semantics and 48 dp touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
