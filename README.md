# TrailNest

A working Android development build and companion website for private walk preparation, GPX geometry and a walking journal. **Version 0.1.0 is not the complete production application described in the brief and is not approved for Appstore submission.**

## Run

Android: JDK 17, Android SDK platform 35 and build tools 35.0.0, with `ANDROID_HOME` configured. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. On Windows use `gradlew.bat`. Install `app/build/outputs/apk/debug/app-debug.apk` on a test device. No Google Play Services or API credentials are required.

Generated APKs and local build reports are excluded from Git. After building the debug, release and device-test APKs, run `node tools/package.mjs` to recreate the development deliverables and checksum manifest under `artifacts/`.

Website: `cd website`, `npm run dev`, then open `http://127.0.0.1:4173`. No dependency installation is required. `npm test` runs its domain checks; `npm run build` emits a standalone static site to `website/dist`. Paths and asset links assume deployment at the site root.

## Implemented

- Kotlin/Compose adaptive tablet rail and phone navigation; original TrailNest vector icon, generated imaginary landscape, light/dark appearance and miles/kilometres.
- Room journal with notes, ratings, favourites, distance/duration totals and confirmed deletion. DataStore preferences and persistent preparation checklist.
- Private, bounded GPX import/export; finite-coordinate checks, XML entity rejection, single continuous route/track policy, 50,000-point/5 MB limits and SHA-256 geometry checks.
- Offline geometry pack marking with one-pack free limit. All imports are already private local files; marking a pack does not download extra content. No map tiles or reviewed directions are included.
- Foreground location using Android LocationManager; nearest-segment progress, deviation threshold, stale-fix and accuracy handling; route geometry preview. Touch controls and optional TextToSpeech with audio focus. Push-to-talk uses an installed system recognition activity; only explicit English commands are parsed.
- Explicit user-initiated Sharesheet plan sharing. No automatic location transmission, background recording or emergency monitoring.
- Browser companion with local checklist, journal, GPX geometry tools, country selection, honest coverage states, privacy/support pages, sitemap and WebSite structured data. Browser and Android data are separate and do not sync.
- Country/locale registries; partial localisation extraction/translation-draft auditing; editorial record validation/retirement; draft listing copy and an executable release evidence gate.

## Missing or blocked

The user confirmed no commercial map/route-content licences. Therefore all catalogue countries remain planned, no routes are seeded, no renderer or tile provider is integrated, and no basemap download claims are made. Licensed route content and editorial approval are required before discovery, filters, route guides and access claims can return real routes.

Amazon Appstore SDK integration, product setup, server receipt verification and production entitlement handling are **not implemented**. The billing abstraction stays unavailable. A server, secrets and Amazon console setup are still required. No real purchase success is simulated.

Other remaining scope: complete string/resource migration and reviewed translations, plural rules, localized website/metadata/screenshots, hosted authorized-partner CMS and reports workflow, custom route planning, validated turn instructions, elevation and terrain/facility metadata, background walk recording, photos, editable journal dates, monthly/yearly summaries, customizable checklist items, download workers/recovery/version updates, full brand-asset set and the eight requested marketing screenshots. These are pending implementation, not merely hidden behind credentials.

The public operator name/support/privacy contact and legal review are not supplied. The proposed commercial title’s availability is not checked. Physical Fire OS testing, accessibility/RTL/large-font review and release signing are pending. Minimum SDK 26 excludes earlier Fire OS devices; assess actual target devices before changing this.

## Verification and release

See `docs/VERIFICATION.md` for actual commands and evidence. `node tools/localise.mjs` produces `artifacts/localisation-report.json`. `node tools/editorial.mjs validate` validates the catalogue. `node tools/release-gate.mjs` intentionally exits nonzero while release evidence is missing. Do not set evidence flags without performing the corresponding validation.

`app/src/androidTest` contains device tests, and `tools/capture-android.ps1` captures real screen evidence. These require a connected device. No passed device run or real Android screenshot is claimed when a device is unavailable.

See `docs/PROVIDER-REVIEW.md` for dated primary sources, `tools/README.md` for translation and editorial tooling, `design/README.md` for the visual system and `marketing/` for honest development listing copy.
