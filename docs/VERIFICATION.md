# Verification evidence

Build date: 23 October 2026. Commands run in `D:\App Projects\TrailNest` using JDK 17, Android SDK 35, AGP 8.11.1 and Gradle 8.13.

## Android 0.3.0 background recording update

The final source passed `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --offline --max-workers=2`. Final build: **BUILD SUCCESSFUL in 1m 34s**, after correcting a journal callback omitted during the notification shortcut change. APK metadata identifies version **0.3.0**, version code **3**.

- Android JVM tests: **32 passed**, zero failures. Ten new recording tests cover active time excluding pauses, segment breaks, accepted movement, inaccurate/stale/future fixes, outages/jumps, duplicate/out-of-order fixes, stale location status, interrupted-draft timing, zero-distance editing, and cached fixes predating resume.
- Tooling tests: **3 passed**. Website tests: **5 passed**; website build passed. The website remains the separate 0.1 preview and was not redeployed.
- Lint: **zero errors, 23 warnings**. The additional warning recommends the SharedPreferences KTX editing helper; the recorder checks the explicit durable commit result. Existing dependency/resource/platform recommendations remain. Existing RouteScreen always-true compiler warnings remain. No warnings were suppressed.
- **9 device tests compiled, 0 executed**. New device coverage checks recording review/save UI and idempotent Room insertion preserving later edits. ADB reported no connected device. No physical Fire OS, GPS, screen-off, permission or notification behavior is claimed as tested; the pending acceptance cases are in `RECORDING.md`.
- Localisation: **94 strings and 3 plural resources** extracted; 19 locale targets audited, zero approved locales. Editorial validation passed with an empty catalogue. The production release gate still correctly fails for the documented licences, translations, Fire validation, billing, signing and other missing release prerequisites.
- Debug, unsigned release and device-test APKs are packaged under `artifacts/TrailNest-0.3.0-*.apk`, with hashes in `artifacts/checksums.json`. `apksigner verify --verbose` verified the debug APK with Signature Scheme v2. APKs are local ignored build deliverables. Machine-readable results: `artifacts/verification-0.3.0.json`.

Recording uses an explicit location foreground service, separate from foreground route progress. Only aggregate duration/distance checkpoints persist; coordinates remain in memory. Interrupted recordings recover paused, and completing a recovered draft uses its stable ID to prevent duplicate journal entries. This is not a recorded-track export feature or emergency monitor. `RECORDING.md` describes behavior and limitations.

## Android 0.2.0 update

The final source passed `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --offline --max-workers=2`. The final repeat after the journal lazy-list change completed successfully. APK metadata identifies version **0.2.0**, version code **2**.

- Android JVM tests: **22 passed**, zero failures (12 original domain checks and 10 personal-planning checks). New coverage includes leap/future dates, timezone month/year boundaries, unchanged-distance preservation, checklist legacy IDs/reset behavior, and daylight-saving/return-time validation.
- `node --test tools/tests/*.test.mjs`: **3 passed**. These check multi-file resource extraction, plural categories/placeholders and actual migration SQL with existing data, foreign keys and cascading photo-row deletion. Node 24’s SQLite API emits its experimental-feature notice. This test does not constitute execution of Room’s Android migration validator.
- Website `npm test`: **5 passed**; `npm run build`: passed. The Android features in this update are separate from the existing website 0.1 preview; no website deployment was changed.
- Android lint: **zero errors, 22 warnings**. Warnings include dependency/SDK upgrades, existing resource/backup/icon recommendations, platform `ExifInterface` versus AndroidX recommendations, and two plural-candidate heuristics for photo ordinal/year labels. No lint warning was suppressed.
- The debug APK passed `apksigner verify --verbose` with APK Signature Scheme v2. Debug, unsigned release and compiled device-test APKs were packaged as `artifacts/TrailNest-0.2.0-*.apk`; `artifacts/checksums.json` was regenerated. The packaging script checks app APK metadata against the declared version before copying.
- Localisation audit: **73 string and 2 plural resources extracted**, 19 locale targets audited, zero locales approved. Existing hardcoded UI and reviewed translations are still incomplete. Editorial validation passed with an empty catalogue. The release evidence gate still correctly fails for the known production prerequisites.
- ADB reported no connected device. The **7 device tests compiled but were not run**, including real Room migration validation, EXIF stripping on a synthetic photo, checklist UI, and journal editing with preserved rating/favourite/distance. No new Android screenshot or Fire OS behavior result is claimed. Checklist UI tests temporarily isolate preferences and restore them afterward; journal/database/photo fixtures clean up their own records/files.

`artifacts/verification-0.2.0.json` contains a machine-readable summary. `PERSONAL-FEATURES.md` describes the implemented behaviors and boundaries.

## Version 0.1 baseline checks

- `gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` — succeeded. Debug APK, unsigned release APK and instrumentation APK produced. Twelve JUnit domain tests passed. Lint passed with warnings about dependency versions/target SDK and platform recommendations; this does not certify Fire compatibility.
- A final repeat rebuilt the updated GPX/audio code and all APKs, and passed all twelve domain tests, then caught an unescaped drive colon in the newly added local SDK configuration. That configuration was corrected; `gradlew.bat :app:lintDebug --offline` then succeeded. No lint errors remain; eighteen warnings remain. The final APKs were packaged after their successful assembly and their SHA-256 hashes regenerated. `apksigner verify --verbose` verified the debug APK with APK Signature Scheme v2.
- `npm test` in `website/` — five tests passed: journal validation, distance, coordinate validation, HTML escaping and country uniqueness.
- `npm run build` — static website produced successfully.
- `node tools/localise.mjs` — extraction/audit completed. Nineteen requested locale targets; no approved translations. Hardcoded Compose strings and plural migration remain. This is an incomplete-localisation report, not a passing localisation claim.
- `node tools/editorial.mjs validate` — passed for the deliberately empty catalogue; no routes published.
- `node tools/release-gate.mjs` — correctly failed for missing release prerequisites; details are in `artifacts/release-gate.json`.

## Browser verification

Used the Codex in-app browser directly, without a Playwright browser fallback. The primary dashboard was viewed at the browser’s original narrow viewport and at the concept’s native 1536×1024 viewport. The original design and captured implementation were opened with `view_image` in the same comparison pass. Evidence: `design/dashboard-concept.png`, `artifacts/website-desktop.png`.

Checked navigation, persistent checklist across reload, creation of an explicitly labelled synthetic journal entry, displayed totals and empty coverage states. Imported `artifacts/qa-fixture.gpx`, saved a pack after SHA-256 verification, opened its geometry and exported a real GPX file. Synthetic QA entries were removed through the confirmation controls after verification; they are never recommendations. The published site was loaded successfully in the same browser. Phone verification at 390×844 reported both viewport and scroll width of 390 px, with no horizontal overflow. Evidence includes `artifacts/website-live-desktop.png`, `artifacts/website-live-mobile.png` and `artifacts/website-gpx-qa.png`.

### Design comparison ledger

| Comparison | Evidence and disposition |
| --- | --- |
| Information hierarchy | Same five navigation destinations, two-line editorial heading, search/filter rail, reviewed-route empty state, preparation panel and three journey shortcuts. |
| Main copy | Heading, tagline, empty-state heading, collection status, coverage action and checklist action match the concept. Preparation descriptions and offline shortcut text deliberately avoid safety or basemap claims. No new promotional claims above the fold. |
| Typography | Serif editorial headings and sans control/body text preserve the intended hierarchy. System fonts are used for offline availability. Exact generated font metrics are not represented as identical. |
| Palette | Ivory, forest, sage and charcoal match the design tokens. Dark mode uses separate accessible surface values; accessibility review remains pending. |
| Artwork | Standalone generated landscape replaces the concept scene. Simplified original vector mark is optimized for small sizes. Decorative signpost/contours were omitted. Imagery is never presented as a real route or route photo. |
| Container rhythm | Fixed tablet/desktop rail, open hero, paired bordered panels and horizontal shortcut rail are retained; narrow views stack sections and use a compact header. |
| Fidelity limit | This is a reviewed implementation of the design direction, not a pixel-identical 10/10 agency sign-off or an approved commercial design. |

## Device limitation

No connected Android device was reported by ADB. A hardware-accelerated emulator launch failed because the Android Emulator hypervisor driver is absent. A software-emulation attempt did not produce a usable ADB device. No driver, security setting or Windows feature was changed. Therefore instrumentation tests were **compiled but not run**, and no Android/Fire screenshot, speech/GPS/purchase test or actual Fire OS compatibility result is claimed.

## Distribution artefacts

`artifacts/TrailNest-0.1.0-debug.apk` is a development-signed installable APK. `artifacts/TrailNest-0.1.0-release-unsigned.apk` is not signed for installation or Appstore submission. `artifacts/TrailNest-0.1.0-device-tests.apk` contains the compiled but unrun instrumentation tests. `artifacts/checksums.json` records SHA-256 hashes. The eight marketing screenshots are intentionally blocked in `marketing/screenshots.json` rather than fabricated from nonexistent route or navigation features. The public development website was deployed successfully through the Sites plugin at https://trailnest-walks.boboyen411891.chatgpt.site. Its immutable source commit/version and deployment IDs are in `artifacts/website-deployment.json`.
