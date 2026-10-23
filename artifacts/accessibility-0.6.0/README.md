# Android 0.6 accessibility evidence

Physical Fire KFTRWI, API 28. Real app composables with synthetic QA fixtures and the default MaterialTheme. Screenshots are test evidence, not marketing assets or human-approved translations. Panes are 360×640 or 800×640 dp; the surrounding blank area is the test host. Selected screenshots use 200% fonts.

All 76 combinations of 19 locales, two widths and 100%/200% fonts passed. Each checks nine screens plus journal editing/cards and recording review. The matrix records 988 control audits, 14386 labelled action observations, 3756 fully visible touch-bound checks, 76 keyboard navigation sequences, 380 native accessibility-focus targets and 304 adjacent native traversal pairs. Repeated observations of the same controls are not unique controls. 13483 placed target observations were measured; 903 unplaced lazy-item observations were counted separately and excluded from size measurements. Journal edit actions were scrolled into view and asserted visible. Placed target allocation must be at least 48 dp; actual hit bounds are additionally checked for fully visible semantic targets measuring at least 48 dp. Scroll-edge clipping is excluded from the latter measurement.

UiAutomation temporarily requests touch exploration; native virtual-node focus flags are refreshed and checked separately from keyboard focus. Native navigation labels, selected state, inactive actions and child order/non-conflicting traversal overrides are checked. VoiceView/TalkBack spoken output and reader-specific swipe behaviour still require human acceptance.

The share captures include the software keyboard covering the lower dialog area. Compose visibility assertions do not establish freedom from that separate-window occlusion; keyboard-open dialog access remains a device acceptance item.

The device remained PIN-locked. Temporary draft test windows display above the lock; global locale/font/rotation/timeout settings and normal-app data were untouched. All translations remain drafts. See ../../docs/ACCESSIBILITY.md and ../../docs/VERIFICATION.md for scope and limits.
