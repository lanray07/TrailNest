# Verification evidence

Build date: 23 October 2026. Commands run in `D:\App Projects\TrailNest` using JDK 17, Android SDK 35, AGP 8.11.1 and Gradle 8.13.

## Executed

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
