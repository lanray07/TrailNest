# TrailNest Amazon setup and acceptance

Status (2026-10-24): Amazon unlocked the existing Test 1 update. The signed `TrailNest-0.8.5-lat.apk` (version code 13) was uploaded and published as **Test in-progress**, under `TrailNest 0.8.5 languages LAT`, using the existing tester group and device targeting. The Kindle updated through Amazon Appstore to 0.8.5/code 13; launch, premium in-app icon, scrolling language picker and French persistence across process restart were verified. The original device-language preference was restored and survived restart. See [update evidence](../artifacts/amazon-lat-0.8.5-update.json), [current values and catalog caveat](../config/billing/README.md) and the sandbox receipt-date limitation below. All 19 locales are active by owner instruction; translation provenance remains recorded without claiming human approval. The annual subscription is approved for TEST only, but the updated app still displays no annual offer. Actual LAT purchase acceptance and public Appstore submission remain pending.

## 1. Subscription and annual price

Open [Amazon Developer Console](https://developer.amazon.com/) → My Apps → TrailNest → View → In-App Items. For a new item choose Add Single IAP → Subscription. Configured identifiers in the unpublished app:

| Field | Configured value |
| --- | --- |
| Display name | TrailNest Premium |
| Parent SKU | `com.trailnest.premium` |
| Annual term SKU | `com.trailnest.premium_term` |
| Term period | Annually |
| Free trial / promotion / add-on | None for this integration |
| Price and currency | GBP 4.99 annually; Amazon-calculated US price USD 6.69 |

Under Subscription Terms choose Add New Term, select the annual period and set the paid price. Review marketplace prices. Describe the current benefit as additional private GPX geometry packs; maps and curated routes are unavailable. Do not advertise unfinished benefits. Save the identifiers exactly, including case. If items already exist, inspect their identifiers before creating duplicates. [Amazon item setup](https://developer.amazon.com/docs/in-app-purchasing/iap-create-and-submit-iap-items.html).

The proposed `_term` annual identifier accommodates Amazon's sandbox convention. With one annual term and no subscription base, sandbox RVS returns `<parent>_term`. If an existing annual SKU differs, stop before testing: the server currently requires an exact term match, so a sandbox-only mapping needs implementation and testing. Do not weaken production checks or rename an existing product blindly. [Sandbox behavior](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html).

## 2. Public keys and secrets

For the selected app/upcoming version, open Upload Your App File → Additional information → View public key. Download `AppstoreAuthenticationKey.pem` and put it in `app/src/main/assets/`. This Amazon public key is different from our receipt-verifier public key. [Amazon key instructions](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).

Generate a separate RSA signing key on each verifier environment using a secure administrative process. Store its private key in hosting secret storage, with read permission only for the service. Export its SPKI public key as `billing-verifier-public.pem` for the corresponding APK. Never reuse the androidTest fixture key. Keep the real RVS shared key from [Amazon Shared Key](https://developer.amazon.com/sdk/shared-key.html) in server secret storage only. Sandbox can use a separate non-empty test value; it does not establish that the live shared key works.

## 3. Hosting configuration

The owner selected the existing Vercel account. The Node 22 Web-handler adapter is deployed to the isolated sandbox project, with a verified POST rate limit of 20/minute/IP. The sandbox Production URL is public by owner instruction; Preview deployments remain protected. The standalone Node service can alternatively run behind a local HTTPS reverse proxy. A static Sites deployment cannot run this verifier.

Use separate sandbox, LAT and production deployments, signing keys and URLs. See [AMAZON-BILLING.md](AMAZON-BILLING.md) for all environment variables and proxy requirements. Set the exact Android package and SKUs server-side. Sandbox uses `AMAZON_RVS_ENV=sandbox`; LAT uses `lat` and the production RVS endpoint; the final live service uses `production` and rejects test/beta receipts. Keep any secret/private key out of Gradle, Git, chat and access logs.

After deployment, check `HEAD https://<environment-host>/v1/amazon/ready` returns 204 without redirects. Test incorrect content types, oversized requests, rate limits and service outages. Readiness alone does not prove Amazon credentials or purchases work. Do not enable checkout until HTTPS, pinned public key and server configuration have been verified.

## 4. App Tester

Install Amazon App Tester on the test Fire device. Export your subscription JSON from the console as `amazon.sdktester.json`. Check it contains only the intended annual offer, the exact parent/term identifiers, annual metadata and no trial/promotion/base/add-on. Use a billing-enabled debug APK with sandbox URL/key/environment and matching package. The existing disabled APKs cannot perform this test.

Run these commands with the selected device's ADB; they intentionally change test mode/overlay permissions. Record and restore prior settings after testing:

```text
adb shell appops set com.amazon.sdktestclient SYSTEM_ALERT_WINDOW allow
adb push <download-path>/amazon.sdktester.json /sdcard/amazon.sdktester.json
adb shell setprop debug.amazon.sandboxmode debug
```

In App Tester choose Appstore SDK APIs, inspect IAP Items in JSON File, then configure User Account Settings, IAP API Response Settings and Subscription Purchase Settings. Start with successful purchase and restore, then cancel, pending approval, denied receipt and network outage. Confirm fulfillment occurs only after signed verification. Exit sandbox with:

```text
adb shell setprop debug.amazon.sandboxmode none
```

Sources: [installation and JSON](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html), [App Tester controls and mode](https://developer.amazon.com/docs/in-app-purchasing/iap-app-tester-user-guide.html).

## 5. Live App Testing

**Latest physical LAT check, 24 October 2026:** Test 1's Amazon-distributed 0.8.1/code 9 installed and launched on KFTRWI/API 28 with sandbox mode off. Amazon's GB SDK session returned no annual offer despite the console's approved TEST subscription/live annual term; Subscribe remains disabled, and restore did not grant Premium. Verifier readiness is 204, but no purchase/receipt/fulfillment acceptance passed. See [sanitized device evidence](../artifacts/amazon-lat-device-2026-10-24.json). The details below retain the earlier setup history. Source 0.8.3 includes the active translations and owner waiver of human translation review, and has not replaced the installed 0.8.1 LAT binary.

Follow-up investigation verified the installed BuildConfig and exact SKU values, the owner-confirmed invited account and SDK handling of an empty catalog map. Clearing Appstore cache and repeating restore did not resolve it; 27 billing JVM and 16 verifier tests passed. With explicit owner authorization, submitted Amazon Support case [22783242591](https://developer.amazon.com/support/cases/22783242591), now **In Process: pending Amazon action**. [Investigation and cleanup record](../artifacts/amazon-lat-catalog-investigation.json). Purchase acceptance remains blocked; no annual-term or receipt safeguards were relaxed.

App Tester status on 23 October 2026: US annual metadata/price, readiness, canceled checkout, successful sandbox checkout callback and SDK restore callback were observed. Real sandbox RVS returned matching IDs/SKUs but an already-past renewal horizon and a `1 Month` term. No entitlement was granted or fulfilled. Successful purchase/restore acceptance remains blocked; do not mark the complete App Tester suite passed. The tablet's original catalog and subscription settings were restored, TrailNest-only test transactions removed and sandbox mode set to `none`. Other apps' transactions were retained. See [sanitized evidence](../artifacts/amazon-setup.json).

The isolated LAT verifier uses Amazon's production RVS endpoint, the real shared key and a separate signing key. Readiness is reachable without redirects; Preview URLs remain behind Vercel Authentication. LAT testers are not charged. The uploaded `0.8.0-lat` APK uses package `com.trailnest.app`, the LAT public pin and endpoint, and `DEBUG=false` / `LAT_BUILD=true`. It excludes draft locale resources and is labelled TrailNest LAT. Build the unsigned LAT variant with:

```text
gradlew.bat :app:assembleLat -PtrailnestLatBillingEnabled=true --offline --max-workers=2
```

Sign with the separate LAT-only Android signing key; never commit the private keystore/password. This machine stores the key outside the repository under the user's `.codex/credentials/trailnest-lat/`, with an ACL restricted to the owner/SYSTEM and a DPAPI-protected password. The shipped signature is RSA-3072, verified with APK schemes v2/v3. Amazon re-signs distribution APKs. Normal releases retain disabled billing defaults and reject attempts to enable LAT entitlements. Do not promote the LAT binary to a live release. [Testing environments](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html), [Amazon signing](https://developer.amazon.com/docs/app-submission/understanding-submission.html).

From the app's Live App Testing dashboard, create a test and complete its required app files/metadata. Make the subscription available to the test using the console's test-only IAP submission option, then add consenting testers to a tester group and associate that group with the test. Starting the test sends Amazon invitations, so review the recipient group before that action. Install through the LAT distribution flow. [IAP in LAT](https://developer.amazon.com/docs/in-app-purchasing/iap-lat.html), [tester management](https://developer.amazon.com/docs/app-testing/manage-testers.html).

On 23 October 2026 the owner explicitly confirmed export compliance and the tester rights/consent/18+ certification. These were applied, the one-member TrailNest group selected and Submit Test accepted. The dashboard currently shows **Test submitted**; Manage testers remains disabled until publication. Invitation delivery has not been observed. The content questionnaire truthfully declares personal-information processing (billing account/receipt identifiers), location services and user-generated content, with no ads/gambling/mature content. Its general warning about personal information was recorded; submission was nevertheless accepted. Amazon's definition includes user IDs/purchase history, so these cannot be omitted merely to pass a questionnaire. Review remains Amazon's decision. [Privacy data definitions](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html).

After publication, open the invitation using the consenting tester's Amazon account and install from Amazon's LAT page/Beta Hub on a selected Fire tablet. Keep `debug.amazon.sandboxmode=none`; do not sideload the APK and call that LAT. The current binary is selected for 12 Fire tablet models, with no TV/Automotive targets. The console warns that other devices are filtered by API requirements; wider device coverage is not claimed. Then buy/restore the TEST-only subscription and verify a signed `lat` lease plus fulfillment before exercising renewals/refunds/outages/account changes. Only after observed success should the acceptance table below be marked passed.

Use accelerated subscriptions where available and selected before starting the test. Record renewal, expiry and cancellation behavior; reset only the intended test users' subscriptions when repeating scenarios. Capture actual observed results with APK hash, environment, device, date and anonymized account reference. Never include shared keys or raw receipt/account IDs in evidence.

## 6. Acceptance before submission

| Scenario | Required observation | Result |
| --- | --- | --- |
| Buy / restore / reinstall | Verified current account gets the documented benefit | Pending |
| Cancel checkout / pending approval | No premature entitlement or fulfillment | Pending |
| Account switch / another device | Current account binding and restore work | Pending |
| Renewal / auto-renew off / expiry / refund / grace | Behavior follows verified receipt status | Pending |
| Offline / verifier outage / fulfillment retry | Bounded cache, no fabricated or extended lease | Pending |
| Restart / reboot / clock changes | Cache restrictions hold | Pending |
| Metadata unavailable / offer changed | Checkout unavailable for unsupported offers | Pending |
| Localized price / renewal wording / accessibility | Human review of enabled purchase flow | Pending |

After LAT passes, create and verify the separately configured production build/server and repeat relevant acceptance. Review all other release gates, signing, privacy/store declarations and human translation approvals. LAT promotion can replace upcoming-version metadata; review it before promoting. Never promote the isolated LAT verifier configuration into the live app. [Test promotion](https://developer.amazon.com/docs/app-testing/manage-test.html).
