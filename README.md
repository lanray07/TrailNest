# TrailNest

A working Android development build and companion website for private walk preparation, GPX geometry and a walking journal. **Android version 0.8.6 is not the complete production application described in the brief and is not approved for Appstore submission.** The companion website remains the separate 0.1 preview.

## Run

Android: JDK 17, Android SDK platform 35 and build tools 35.0.0, with `ANDROID_HOME` configured. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. On Windows use `gradlew.bat`. Install `app/build/outputs/apk/debug/app-debug.apk` on a test device. No Google Play Services or API credentials are required.

Generated APKs and local build reports are excluded from Git. After building the debug, release, translation-preview and device-test APKs, run `node tools/package.mjs` to recreate the development deliverables and checksum manifest under `artifacts/`.

Website: `cd website`, `npm run dev`, then open `http://127.0.0.1:4173`. No dependency installation is required. `npm test` runs its domain checks; `npm run build` emits a standalone static site to `website/dist`. Paths and asset links assume deployment at the site root.

## Implemented

- Kotlin/Compose adaptive tablet rail and scrollable phone/large-font navigation, wrapping actions and RTL-aware text direction; premium evergreen/gold TrailNest icon, generated imaginary landscape, light/dark appearance and miles/kilometres.
- Editable Room journal with completed-walk dates, notes, ratings, favourites and a favourites filter. Monthly activity and selectable yearly summaries use the device time zone. Editing notes preserves the original distance without rounding drift.
- Background walk recording: explicit start, pause/resume, finish/review/save and discard, with a location foreground service and notification controls. Active duration excludes pauses; interrupted drafts recover paused. Only aggregate estimated distance and duration are persisted, with duplicate-safe journal saves. See `docs/RECORDING.md` for device limitations.
- Private walk photos: explicit system file picker, up to eight per saved walk, 20 MB input limit, sampled decoding, orientation correction, and app-private resized JPEG copies with EXIF/location metadata removed. Removing copies leaves original photos untouched. Room schema 1→2 migration retains existing journal entries and imported routes; deleting a walk cascades its photo rows and removes private files.
- Custom checklists: add, rename, reorder and remove items, atomically reset checked states, or confirm restoring defaults. Existing v0.1 checklist progress migrates on first edit. Checklist progress and journal walk counts use Android plural resources.
- Private, bounded GPX import/export; finite-coordinate checks, XML entity rejection, single continuous route/track policy, 50,000-point/5 MB limits and SHA-256 geometry checks.
- Offline geometry pack marking with one-pack free limit. All imports are already private local files; marking a pack does not download extra content. No map tiles or reviewed directions are included.
- Foreground location using Android LocationManager; nearest-segment progress, deviation threshold, stale-fix and accuracy handling; route geometry preview. Touch controls and optional TextToSpeech with audio focus. Push-to-talk uses an installed system recognition activity; only explicit English commands are parsed.
- Explicit user-initiated Sharesheet plan sharing with editable estimated duration, required future return time (including time-zone/offset in shared text), and optional contact details. Ambiguous/nonexistent daylight-saving times are rejected. No automatic location transmission or emergency monitoring. The whole share form scrolls above the software keyboard, with Next/Done input actions; see `docs/SHARE-KEYBOARD.md`.
- Browser companion with local checklist, journal, GPX geometry tools, country selection, honest coverage states, privacy/support pages, sitemap and WebSite structured data. Browser and Android data are separate and do not sync.
- Country/locale registries; resource extraction across all default values XML files, plural-category/placeholder validation, translation-draft auditing; editorial record validation/retirement; draft listing copy and an executable release evidence gate. Displayed app text and validation/share messages are resource-backed. All 22 configured locales are active in every Android variant (261 strings and four plural resources each), with an in-app Settings language picker (native language names, device-default reset and persisted choice on API 26+ Fire tablets), with English fallback. The owner has waived the human translation-review requirement; draft notices are removed, and provenance/review history remains recorded. A separate translation-preview package isolates synthetic review data. A repeatable on-device matrix checks normal/200% fonts and narrow/tablet layouts; comprehensive accessibility review remains pending. See `docs/LOCALISATION.md` and `docs/VERIFICATION.md` for executed evidence.

Screen-reader labels identify repeated action targets; settings controls expose roles/states, headings and pages have navigation semantics, and the compact menu supports keyboard focus and dismissal. The locale matrix also audits accessibility labels, traversal and touch targets. See `docs/ACCESSIBILITY.md` for scope and human acceptance limits.

Localized Amazon listing copy and eight native screenshots per locale are prepared for all 22 app locales, including Traditional Chinese, Turkish and Indonesian. See [the asset gallery](marketing/README.md). The 13 registry storefront mappings have complete copy; remaining language assets are supplemental pending console mapping review. Assets use localized example data in the isolated preview package and have agent visual review under the existing translation waiver; no native-speaker certification or Amazon upload is claimed.

## Missing or blocked

The user confirmed no commercial map/route-content licences. Therefore all catalogue countries remain planned, no routes are seeded, no renderer or tile provider is integrated, and no basemap download claims are made. Licensed route content and editorial approval are required before discovery, filters, route guides and access claims can return real routes.

Amazon Appstore SDK 3.0.9 purchase/restore handling, a server-side RVS verifier and account-bound signed entitlement caching are implemented. Amazon subscription SKUs/public keys and separate hosted sandbox/LAT verifiers are configured. Normal release purchases remain disabled. Test 1's former catalog blocker was resolved; Test 2 completed its free LAT purchase, four accelerated renewals, server-side expiry and final expired-state online/offline checks. Original event-time device revocation latency, refund/grace/account-switch acceptance and separate production billing configuration remain incomplete. The default build grants no paid entitlement. See `docs/AMAZON-BILLING.md` and `docs/AMAZON-SETUP.md` for configuration and evidence.

Other remaining scope: comprehensive accessibility review, additional device acceptance checks, localized website, hosted authorized-partner CMS and reports workflow, custom route planning, validated turn instructions, elevation and terrain/facility metadata, download workers/recovery/version updates, full brand-asset set. These are pending implementation, not merely hidden behind credentials.

The owner supplied banksmi@mail.com as the public support/privacy contact. The privacy/support pages describe recording, recovery, local deletion and hosted receipt verification; complete operator legal details, provider log-retention arrangements and legal review remain pending. The proposed commercial title’s availability is not checked. Fire layout/UI/data checks are recorded in `docs/VERIFICATION.md`; broader Fire acceptance, human accessibility review and production release signing remain pending. Minimum SDK 26 excludes earlier Fire OS devices; assess actual target devices before changing this.

## Verification and release

See [the current Amazon release review](docs/AMAZON-RELEASE-REVIEW.md) for prioritized remaining blockers and completed LAT evidence. See `docs/VERIFICATION.md` for actual commands and evidence, and `docs/PERSONAL-FEATURES.md` for the 0.2 feature details. `node tools/localise.mjs` produces `artifacts/localisation-report.json`. `node tools/editorial.mjs validate` validates the catalogue. `node tools/release-gate.mjs` intentionally exits nonzero while release evidence is missing. Do not set evidence flags without performing the corresponding validation.

`node --test tools/tests/*.test.mjs` runs resource/plural regressions and an SQLite regression test executing the actual Kotlin migration SQL. It requires Node 24 with `node:sqlite` (experimental in the tested runtime). This SQL check complements, but does not replace, the device test that opens the migrated database through Room.

`app/src/androidTest` contains device tests, and `tools/capture-android.ps1` captures real screen evidence. These require a connected device. No passed device run or real Android screenshot is claimed when a device is unavailable.

See `docs/PROVIDER-REVIEW.md` for dated primary sources, `tools/README.md` for translation and editorial tooling, `design/README.md` for the visual system and `marketing/` for honest development listing copy.
