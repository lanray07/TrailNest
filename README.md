# TrailNest

A working Android development build and companion website for private walk preparation, GPX geometry and a walking journal. **Android version 0.6.0 is not the complete production application described in the brief and is not approved for Appstore submission.** The companion website remains the separate 0.1 preview.

## Run

Android: JDK 17, Android SDK platform 35 and build tools 35.0.0, with `ANDROID_HOME` configured. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. On Windows use `gradlew.bat`. Install `app/build/outputs/apk/debug/app-debug.apk` on a test device. No Google Play Services or API credentials are required.

Generated APKs and local build reports are excluded from Git. After building the debug, release, translation-preview and device-test APKs, run `node tools/package.mjs` to recreate the development deliverables and checksum manifest under `artifacts/`.

Website: `cd website`, `npm run dev`, then open `http://127.0.0.1:4173`. No dependency installation is required. `npm test` runs its domain checks; `npm run build` emits a standalone static site to `website/dist`. Paths and asset links assume deployment at the site root.

## Implemented

- Kotlin/Compose adaptive tablet rail and scrollable phone/large-font navigation, wrapping actions and RTL-aware text direction; original TrailNest vector icon, generated imaginary landscape, light/dark appearance and miles/kilometres.
- Editable Room journal with completed-walk dates, notes, ratings, favourites and a favourites filter. Monthly activity and selectable yearly summaries use the device time zone. Editing notes preserves the original distance without rounding drift.
- Background walk recording: explicit start, pause/resume, finish/review/save and discard, with a location foreground service and notification controls. Active duration excludes pauses; interrupted drafts recover paused. Only aggregate estimated distance and duration are persisted, with duplicate-safe journal saves. See `docs/RECORDING.md` for device limitations.
- Private walk photos: explicit system file picker, up to eight per saved walk, 20 MB input limit, sampled decoding, orientation correction, and app-private resized JPEG copies with EXIF/location metadata removed. Removing copies leaves original photos untouched. Room schema 1→2 migration retains existing journal entries and imported routes; deleting a walk cascades its photo rows and removes private files.
- Custom checklists: add, rename, reorder and remove items, atomically reset checked states, or confirm restoring defaults. Existing v0.1 checklist progress migrates on first edit. Checklist progress and journal walk counts use Android plural resources.
- Private, bounded GPX import/export; finite-coordinate checks, XML entity rejection, single continuous route/track policy, 50,000-point/5 MB limits and SHA-256 geometry checks.
- Offline geometry pack marking with one-pack free limit. All imports are already private local files; marking a pack does not download extra content. No map tiles or reviewed directions are included.
- Foreground location using Android LocationManager; nearest-segment progress, deviation threshold, stale-fix and accuracy handling; route geometry preview. Touch controls and optional TextToSpeech with audio focus. Push-to-talk uses an installed system recognition activity; only explicit English commands are parsed.
- Explicit user-initiated Sharesheet plan sharing with editable estimated duration, required future return time (including time-zone/offset in shared text), and optional contact details. Ambiguous/nonexistent daylight-saving times are rejected. No automatic location transmission or emergency monitoring.
- Browser companion with local checklist, journal, GPX geometry tools, country selection, honest coverage states, privacy/support pages, sitemap and WebSite structured data. Browser and Android data are separate and do not sync.
- Country/locale registries; resource extraction across all default values XML files, plural-category/placeholder validation, translation-draft auditing; editorial record validation/retirement; draft listing copy and an executable release evidence gate. Displayed app text and validation/share messages are resource-backed. All 19 configured locales have complete unreviewed draft bundles (249 strings and four plural resources each). A separate translation-preview build isolates those drafts from normal builds. A repeatable on-device matrix checks normal/200% fonts and narrow/tablet layouts; human linguistic and comprehensive accessibility review remain pending. See `docs/LOCALISATION.md` and `docs/VERIFICATION.md` for executed evidence.

Screen-reader labels identify repeated action targets; settings controls expose roles/states, headings and pages have navigation semantics, and the compact menu supports keyboard focus and dismissal. The locale matrix also audits accessibility labels, traversal and touch targets. See `docs/ACCESSIBILITY.md` for scope and human acceptance limits.

## Missing or blocked

The user confirmed no commercial map/route-content licences. Therefore all catalogue countries remain planned, no routes are seeded, no renderer or tile provider is integrated, and no basemap download claims are made. Licensed route content and editorial approval are required before discovery, filters, route guides and access claims can return real routes.

Amazon Appstore SDK integration, product setup, server receipt verification and production entitlement handling are **not implemented**. The billing abstraction stays unavailable. A server, secrets and Amazon console setup are still required. No real purchase success is simulated.

Other remaining scope: human linguistic and accessibility review, additional device acceptance checks, localized website/metadata/screenshots, hosted authorized-partner CMS and reports workflow, custom route planning, validated turn instructions, elevation and terrain/facility metadata, download workers/recovery/version updates, full brand-asset set and the eight requested marketing screenshots. These are pending implementation, not merely hidden behind credentials.

The public operator name/support/privacy contact and legal review are not supplied. The proposed commercial title’s availability is not checked. Fire layout/UI/data checks are recorded in `docs/VERIFICATION.md`; broader Fire acceptance, human accessibility/language review and release signing remain pending. Minimum SDK 26 excludes earlier Fire OS devices; assess actual target devices before changing this.

## Verification and release

See `docs/VERIFICATION.md` for actual commands and evidence, and `docs/PERSONAL-FEATURES.md` for the 0.2 feature details. `node tools/localise.mjs` produces `artifacts/localisation-report.json`. `node tools/editorial.mjs validate` validates the catalogue. `node tools/release-gate.mjs` intentionally exits nonzero while release evidence is missing. Do not set evidence flags without performing the corresponding validation.

`node --test tools/tests/*.test.mjs` runs resource/plural regressions and an SQLite regression test executing the actual Kotlin migration SQL. It requires Node 24 with `node:sqlite` (experimental in the tested runtime). This SQL check complements, but does not replace, the device test that opens the migrated database through Room.

`app/src/androidTest` contains device tests, and `tools/capture-android.ps1` captures real screen evidence. These require a connected device. No passed device run or real Android screenshot is claimed when a device is unavailable.

See `docs/PROVIDER-REVIEW.md` for dated primary sources, `tools/README.md` for translation and editorial tooling, `design/README.md` for the visual system and `marketing/` for honest development listing copy.
