# Paid release integration — 1 November 2026

The owner selected OpenFreeMap, MapLibre Native and Cloudflare Workers, authorized Codex to perform the route desk review, and requested the complete paid product before Amazon submission. Test 1 and the installed LAT app remain unchanged. No new purchase, reset, installation over LAT or production app submission has occurred.

## Implemented

- Android candidate **0.8.7 / code 15** adds MapLibre Native 11.8.2, pinned to a stable version compatible with the current Kotlin 2.1 toolchain. Route maps load only after an explicit action that explains transmission of IP address and viewed area to OpenFreeMap/Cloudflare. Route geometry and fresh local position are rendered as local GeoJSON layers. The geometry overview and spoken progress remain available.
- OpenFreeMap Liberty uses an app-specific User-Agent, HTTPS, one allowed resource host, disabled redirects and a shared four-request dispatcher. Offline regions include style, tiles, sprites and glyphs for zooms 0–14. Downloads pause when leaving the screen/background. Saved-region metadata and SDK completion counts distinguish partial from complete downloads across process restarts. Delete removes the region; ordinary ambient cache can remain until app data is cleared.
- A conservative shared **40 MB / 2,800 resources per process session** budget includes all map resources and remains in force after cancellation/resume. The UI discloses this budget. A complete region must also stay within 50 MB and 3,000 tiles. Oversized areas fail preflight or stop; no partial map is advertised as complete. Large CJK font sets and real Fire behaviour still need device acceptance.
- Two **ODbL-1.0 Tamsin Trail one-way sections** are bundled from preserved OSM data. All 42 underlying ways designate pedestrian use. Current Royal Parks information supports walking and imposes autumn night access restrictions. The complete source loop has a roughly four-metre gap and remains unpublished; no connector was invented. Both published sections are continuous and disclose **AI desk review; not field checked**, review date, daylight use, shared/uneven paths, independent return planning and current notices. No difficulty/accessibility or turn-instruction certification is asserted.
- `config/editorial-policy.json` records the owner's explicit delegation of review to Codex. `humanApproved` remains false. Build-time and release validators require that policy, source attribution, dated access review, continuous geometry and candid limitations. Drafts and unresolved geometry do not enter the generated APK catalogue. GPX exports preserve attribution/licence/source metadata, and the Room 2→3 migration retains existing data with nullable provenance.
- All 22 app locales now contain **277 strings and four plurals**, with new map, budget, review and paid-benefit copy. Existing owner translation-review waiver applies; these are AI drafts with truthful provenance, not human-approved translations. Dates and budget numbers use the selected locale.
- A separate Cloudflare production Worker is deployed at `https://trailnest-billing-production.lanraybanks.workers.dev`. The rate binding provisioned successfully. The handler retains the existing receipt/date/package/account/annual-SKU guards and fixes the production environment. Missing configuration fails closed; it cannot accept LAT/test receipts. Requests and RVS responses are bounded, responses are noncacheable and error details are redacted. Cloudflare's limiter is approximate per location; the additional eight-request concurrency cap is per isolate.
- A distinct production verifier RSA identity is held outside Git with current-user DPAPI. Only the public pin is in `app/src/release/assets/`; the sandbox and LAT pins remain unchanged. `tools/build-production.ps1 -Paid` requires the retained production pin and live readiness before creating a signed paid build. Default builds still have purchasing disabled.
- GitHub App checks cover provenance/localization, verifier and tooling tests, Worker dry-run, Android compilation/JVM tests and lint. This workflow does not deploy or submit the app.

## Verified so far

- 67 Android JVM tests passed, both final draft APKs compiled and lint completed with zero errors / 56 warnings. Final build results are recorded in the sanitized progress artifact.
- 24 backend tests and 23 tooling tests pass. SQLite migration checks preserve previous routes/walks, source provenance and photo cascade behaviour.
- Cloudflare's dry-run and actual deployment succeeded. Last observed startup time was 5 ms; **startup time is not per-request CPU acceptance**.
- An isolated **local workerd** fixture exercised the actual Worker adapter, rate binding and Node RSA signing: readiness 204, synthetic receipt response 200 with cryptographically verified signature, malformed request 400. It used an ephemeral QA key, draft package identity and synthetic receipt; **no real Amazon verification or production grant was tested by that fixture**. Its observed wall latency is not a free-tier CPU measurement.
- New instrumentation builds include explicit online-download/render and cold-offline phases. The offline phase rejects a still-connected network. These tests remain unexecuted because ADB currently exposes no connected Kindle.

## Submission blockers

1. The existing real RVS key was located in the LAT Vercel project as a **write-only Secret**. Its value is not readable; no usable local copy was found. Both authenticated-browser and Windows Computer Use tools currently fail before startup with `failed to write kernel assets: The system cannot find the path specified. (os error 3)`, including after reset. Amazon's shared-key page cannot yet be read. No secret was reset, exposed or extracted from a running LAT service. The production Worker has no secrets uploaded and readiness returns **503**.
2. Reconnect the Kindle for actual renderer, fonts, lifecycle, interrupted download, deletion, offline cold restart, accessibility and paid-device acceptance. Use only `com.trailnest.app.draft` for new development checks. Restore original networking afterwards; do not replace the Amazon-installed LAT package.
3. Measure deployed verifier CPU/cold-start behaviour against the Workers Free allowance, verify upstream Amazon credentials, then build the paid production binary with the dedicated key/pin. Retain rejection of test receipts in production. The historical LAT lifecycle evidence remains valid for its original build, not automatic acceptance of this new host/build.
4. Review the prepared privacy additions, update Amazon disclosures/IAP production state and finish title/device acceptance. Refresh all version-specific localized listings and native screenshots after the final build; **0.8.6 assets are intentionally rejected as stale for 0.8.7**. No old screenshot or listing was relabelled as newly verified.
5. Run `node tools/release-gate.mjs` and resolve its remaining evidence failures before submission. Amazon export authorization is already recorded in the conversation; submission authorization remains conditional on the complete paid product.

## Resuming securely

Recover the browser runtime and visit [Amazon Shared Key](https://developer.amazon.com/sdk/shared-key.html). The owner has authorized finding and using that key for Cloudflare; keep its value out of chat, logs and source control. An alternative local hidden-input path is:

```powershell
pwsh -File tools/configure-cloudflare-billing.ps1 -SetAmazonSecret -UploadSecrets
```

This script stores the shared key with current-user DPAPI outside Git and uploads it alongside the already-created independent production signing key. It refuses implicit signing-key rotation. The current-user backup alone does not provide cross-machine disaster recovery.

Build draft instrumentation with:

```powershell
.\gradlew.bat :app:assembleTranslationPreview :app:assembleTranslationPreviewAndroidTest -PtrailnestTestBuildType=translationPreview --max-workers=2
```

Run only `MapProviderDeviceTest` with `mapProviderAcceptance=true` and `mapPhase=download`, then repeat with `mapPhase=offline` after a real network disconnect and process restart. Do not report a merely compiled or skipped provider test as passed.

## Primary provider/review sources

[OpenFreeMap terms](https://openfreemap.org/tos/), [licence inventory](https://github.com/hyperknot/openfreemap/blob/main/LICENSE.md), [MapLibre Android](https://maplibre.org/maplibre-native/android/examples/getting-started/), [OSM copyright](https://www.openstreetmap.org/copyright), [Royal Parks current notices](https://www.royalparks.org.uk/visit/parks/richmond-park), [park access FAQ](https://www.royalparks.org.uk/visit/parks/richmond-park/faqs), [Workers rate limiting](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/), [Vercel write-only Secrets](https://vercel.com/docs/environment-variables/sensitive-environment-variables).
