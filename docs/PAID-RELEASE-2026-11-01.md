# Paid release checkpoint â€” 2 November 2026

The owner selected OpenFreeMap, MapLibre Native and Cloudflare Workers, authorized an AI route desk review and requested the full paid product before Amazon submission. Test 1, its SKU, testers, subscriptions, LAT configuration and installed package remain unchanged. Device installations and test fixtures use only `com.trailnest.app.draft`.

## Current candidate

TrailNest 0.8.7, code 16, has a dedicated retained production app-signing key and independent production verifier pin. The signed paid APK built successfully with 67 JVM tests and release lint; APK signature schemes v2/v3 and the retained certificate were verified. SHA-256: `6019e869b63e1d4ddf07b7d7d58feebb85e170553f1624f75db65846ecadf7d2`. The production draft replacement is saved and code16 is visibly confirmed in Amazon; no app submission has occurred.

A separate annual IAP, `com.trailnest.premium.production` / `com.trailnest.premium.production.annual`, was created with accurate offline-map/auto-renewal descriptions, twelve Console-supported localizations and Â£4.99 annual base pricing, without trial or promotion. Amazon shows **Approved**. The old IAP remains unchanged after its earlier promotion; its Approved metadata is locked. Only production configuration now uses the new SKU. LAT/sandbox identifiers and pins remain unchanged.

The dedicated Cloudflare Worker deployment `6a37c537-b9c3-4f55-9829-2d08b31d80b2` retains strict receipt, account, package, dates, production flags and annual-SKU checks. Production rejects the old LAT SKU and test/beta receipts. Secrets are encrypted with current-user DPAPI outside Git; no receipt database or application request logs are enabled. Readiness was 204. A live clearly nonexistent receipt produced Amazon 400 and verifier 403, with no lease. A tiny deployed readiness/negative-path sample measured CPU p50 5,557Âµs / p99 6,184Âµs (3 requests, 0 errors); this does not establish successful production-grant CPU acceptance. Local workerd signing checks used an ephemeral QA key and synthetic data. No real production payment or entitlement grant has been tested.

## Maps and route content

MapLibre Native 11.8.2 renders attributed OpenFreeMap Liberty maps after explicit network disclosure. HTTPS host restrictions, disabled redirects, four concurrent requests, a shared 40MB/2,800-resource session budget and completion ceilings remain enforced. Offline downloads pause in the background and distinguish partial/complete state. Installed Android ideograph fonts avoid excessive remote CJK glyph requests.

The TNMAP2 fix expands the saved initial viewport to include camera padding and up to 4:1 landscape aspect. Fire HD 10 (2021), KFTRWI/API28, passed full online rendering and cold-process offline rendering with no network, after ambient-cache clearing: 171 tiles, 567 resources, 34,080,017 bytes. Immediate cancel/resume passed with exactly one region: 186 tiles, 582 resources, 34,540,670 bytes. Wi-Fi was restored. Arbitrary panning outside a downloaded area is not promised. Earlier TNMAP1 observations are historical, not substituted for current acceptance.

Two continuous ODbL-1.0 Tamsin Trail sections are bundled with preserved OSM source and attribution. They disclose **AI desk review; not field checked**, daylight/current-notice limitations and independent return planning. The original loop with a source endpoint gap remains unpublished; no connector was invented. No human field, accessibility, difficulty or turn-safety certification is claimed. Broader country coverage remains planned.

## Localization and store assets

All 22 app locales pass validation: 278 strings and four plurals, under the existing owner human-translation-review waiver. The real-device layout matrix passed all 88 configurations (360/800dp, font scales 1/2), with zero failures. Automated accessibility audits are not human VoiceView acceptance.

176 fresh 1920Ã—1200 native screenshots were exported and visually reviewed by Codex. All sharing images were recaptured with the keyboard dismissed and the action visible. Map screenshots contain real attributed basemaps with explicitly synthetic private GPX demo geometry, not a claim of a recorded walk or field-reviewed route.

The production Console saved and reload-verified 15 localized descriptions and 11 sets of eight screenshots (88 images). The Console offers fewer store slots than app languages; remaining local assets are retained without claiming they were uploaded. The IAP offers twelve separate localization slots, including Simplified Chinese.

## Privacy and naming

The owner confirmed **O. Bankole, United Kingdom**, and authorized GitHub Pages URLs. Source privacy/support pages describe local storage/deletion, optional voice providers, optional map-area/IP transmission, Amazon/Cloudflare receipt verification and provider infrastructure metadata. Support correspondence has no asserted fixed retention period. These disclosures are an engineering account of current behavior, not a completed legal assessment.

A same-name Apple camping journal by another developer was found. The owner explicitly chose to keep TrailNest for Amazon. This is a recorded naming decision, not trademark clearance.

## Checks and unresolved acceptance

48 backend/tooling tests pass (25 backend, 23 tooling), including the new production-SKU isolation regression. Route provenance and fresh translation checks pass. Signed paid code16 build, JVM tests and release lint pass. The scoped core device suite passed all 13 tests, including recording, migration, billing layouts and real keyboard interaction for en-US/ar/de/ja/hi at 200% font and 360/800dp. The first broad run was invalidated by a locked Fire keyguard plus a stale test-only migration registration; both were resolved. A targeted unlocked keyboard repro and final core suite passed. No app keyboard defect was reproduced; the harness now fails clearly when keyguard restricts input.

The internal release gate still reports missing complete target-device/production-purchase acceptance, human screen-reader review, full operator/privacy acceptance and country coverage acceptance. These flags have not been fabricated or silently waived. Amazon approval of an IAP is not app approval or end-to-end production billing proof. App submission remains pending resolution of these acceptance requirements and the action-time export declaration.

## Reproduction

- `node --test billing-server/tests/*.test.mjs tools/tests/*.test.mjs`
- `node tools/localise.mjs --check --release`
- `node tools/editorial.mjs validate`
- `pwsh -File tools/build-production.ps1 -Paid -Checks`
- `pwsh -File tools/test-map-device.ps1 -Phase download` (then interrupt/offline individually, exclusive authorized Kindle only)
- `node tools/release-gate.mjs`

Private report folders contain build/device verdicts and sanitized Console upload ledgers. Do not commit secrets, account/receipt identifiers, authentication logs or presigned attachment URLs. The retained DPAPI keys require an independent owner-managed recoverable backup for cross-machine disaster recovery.
