# Android 0.5 layout evidence

Real device screenshots from the draft package on a Fire KFTRWI, API 28. These are synthetic QA fixtures using the default MaterialTheme and real app screen composables. They are not marketing screenshots or human-approved translations. The surrounding blank area belongs to the test host; panes are 360×640 or 800×640 dp. Selected images use 200% fonts.

The JSON contains all 76 combinations of 19 locales, two widths and two font scales (100%/200%), with nine screens each and no measured text overflow. Additional assertions exercise journal editing, recording review, navigation direction/reachability and share-dialog field/action visibility. Configured Android view contexts ensure dialogs also use the selected language/font.

The device remained PIN-locked: only the temporary draft test windows displayed above its lock screen. Global language, font, rotation and timeout settings were not changed; the normal app's data was untouched. The test windows' flags end with their activities. See ../../docs/VERIFICATION.md for complete checks and limits.
