# Amazon billing integration — prepared, disabled

Android 0.8 integrates the native Amazon Appstore SDK **3.0.9** and a separate Node receipt-verification service. The initial implementation shipped disabled. On 23 October 2026, the Amazon app/subscription, Amazon public key and hosted sandbox were configured, followed by an isolated LAT verifier with the actual RVS shared key. The subscription was approved for TEST only. A signed LAT APK is uploaded to the LAT draft; see [current setup](../config/billing/README.md). **Normal builds remain disabled by default. LAT execution and production signing/service/acceptance remain incomplete.** The Amazon release gate remains unapproved. Optional one-time packs, trial/promotional offers, add-ons and tier changes are not implemented; unsupported offers are not sold as a simple annual plan.

## Purchase and restore flow

The application registers its SDK listener on the main thread only when billing is explicitly configured. Resume/Restore confirms the current Amazon IAP user, fetches the actual annual term's product metadata and pages through purchase updates with a full reset. Only a subscription term reported as `Annual`, with a non-empty Amazon price and no unhandled promotion/trial, enables checkout. On the physical Fire device, App Tester echoes the console export's `Annually` spelling; the adapter normalizes that exact alias only in sandbox mode. Production checks stay strict. Sandbox debug diagnostics record callback status/public period flags only, never account or receipt IDs. Amazon handles its checkout and cancellation/failure UI. Pending approvals do not grant or fulfill anything. A backend readiness request must succeed before a new checkout starts.

Receipt IDs and Amazon IAP user IDs go over HTTPS to `/v1/amazon/verify`. The backend chooses its own fixed Amazon RVS endpoint/environment and allowed parent/annual SKUs; the request cannot supply price, expiry, SKU, sandbox mode or an entitlement flag. RVS success is checked against receipt ID, product, term, type, dates and test flags. Revoked/invalid receipts are denied; throttling, credential errors and service outages remain retryable. Grace can extend a past renewal; a refund/cancellation effective now cannot be overridden by grace. Disabling auto-renew does not by itself revoke a currently active receipt.

The backend returns a compact RSA-SHA256 signed lease, not an authority-bearing JSON boolean. Its payload binds the application ID, environment, SHA-256 account/receipt identifiers, parent SKU, annual SKU, issue time and expiry. Android checks the pinned public key and every binding. The lease lasts **at most six hours**, capped by relevant cancellation/renewal/grace dates. A valid RVS receipt without a future renewal date gets only that short revalidation lease, never perpetual access. Tokens for sandbox, LAT, another account, another receipt, another SKU or another app cannot unlock production.

A verified lease must be durably saved before notifying Amazon `FULFILLED`. Replayed receipts are idempotently verified and acknowledged again. Stale request IDs, older verification batches and account switches cannot write or fulfill. Restore aggregates pages so an old canceled receipt does not overwrite a newer valid purchase. Recent checkout receipts are retained in memory to bridge a resume/history race and are still reverified with RVS. No consumable inventory is implemented.

Offline access requires a current SDK-confirmed Amazon account and a valid signed cache. Monotonic elapsed time, wall time and boot count invalidate reboot/rollback/clock-change reuse; where boot count is unavailable a per-process nonce requires fresh verification after process restart. The UI refreshes its local deadline periodically and each pack-limit decision checks the lease again. A definitive denial suppresses the cache in memory even if disk clearing fails. Offline revocation can lag by the remaining lease (at most six hours); this is not real-time revocation. The server is stateless and does not yet consume real-time notifications or provide a persistent subscription ledger.

Premium currently lifts the **private GPX geometry-pack limit only**. It does not create map licences, enable unavailable maps/curated routes or promise unfinished Premium features. Existing free checklist/journal/import/export behavior remains available. No purchase control appears in the default unconfigured panel.

## Backend deployment configuration

For Vercel use `api/ready.mjs` and `api/verify.mjs`, with the included `vercel.json`, Node 22 and a dedicated project per Amazon environment. `BILLING_SIGNING_KEY_BASE64` is a Secret containing base64-encoded PKCS#8 PEM, instead of the standalone server's key-file path. Scope secrets only to the intended deployment environment. The hosted adapter requires `VERCEL=1` and `BILLING_EDGE_RATE_LIMIT_CONFIRMED=true`; set the latter only after publishing and checking a project WAF rule. Otherwise readiness and verification fail closed. It trusts the platform's `x-vercel-forwarded-for`, bounds request bodies/time/concurrency, returns no-store responses and does not log identities or secret-bearing URLs. In-memory limits apply per instance and do not replace the WAF. Serve only `public/` statically; private server modules must return 404 as static paths. Preview deployments remain protected. See [Vercel Node runtime](https://vercel.com/docs/functions/runtimes/node-js) and [platform headers](https://vercel.com/docs/headers/request-headers).

The dependency-free service in `billing-server/` requires Node 22 or later. Run `npm test` there. `npm start` refuses to start without valid configuration. Configure these **on the server**, using a deployment secret store:

| Variable | Meaning |
| --- | --- |
| `AMAZON_RVS_ENV` | `production`, `sandbox`, or isolated `lat`; no automatic fallback |
| `AMAZON_PARENT_SKU` | Actual parent subscription SKU |
| `AMAZON_ANNUAL_SKU` | Actual annual term SKU |
| `BILLING_APPLICATION_ID` | Exact APK package, including `.draft` where applicable |
| `AMAZON_RVS_SHARED_SECRET` | Amazon developer shared key; never in Android or source control |
| `BILLING_SIGNING_KEY_FILE` | Secret-mounted RSA private key, at least 2048 bits, PKCS#8/PEM |
| `BILLING_TRUST_LOOPBACK_TLS_PROXY` | `true` only behind a local trusted TLS reverse proxy |
| `PORT` | Loopback listener, default 8080 |

Export the corresponding SPKI `BEGIN PUBLIC KEY` into Android's `billing-verifier-public.pem`. Keep private keys outside the checkout or in ignored `billing-server/secrets/` / `*.private.pem` files. Do not reuse ephemeral test fixture keys. The backend returns no-store responses, bounds request/RVS bodies, uses timeouts, limits concurrency/rate and does not log receipt IDs or secret-bearing RVS URLs. It never follows RVS redirects. The server listens on loopback; expose it through HTTPS with operational monitoring and edge limits. A trusted local proxy must overwrite `X-Forwarded-Proto` and set a single validated `X-Forwarded-For` client IP, never append untrusted client values. Do not enable proxy trust for direct public HTTP.

The IAP user/receipt pair is bearer proof checked by Amazon RVS, not a general app login. Protect it in transit and avoid logging it. The signed result is checked against the SDK's current account on the untampered client. This endpoint is not authorization for hosted maps, a CMS or other users' data. Future hosted content needs its own authenticated account/authorization model and abuse controls; rooted/modified clients are not claimed to be tamper-proof. Readiness proves service configuration/reachability, not validity of the Amazon shared key; App Tester/LAT must verify the latter before launch.

## Android configuration

Download the app's actual `AppstoreAuthenticationKey.pem` from Amazon Developer Console into `app/src/main/assets/`. Add only the verifier's **public** key as `billing-verifier-public.pem` in that directory. Set these Gradle properties in a local/CI configuration:

```properties
trailnestBillingEnabled=true
trailnestBillingEnvironment=sandbox
trailnestBillingParentSku=<actual-parent-sku>
trailnestBillingAnnualSku=<actual-annual-term-sku>
trailnestBillingVerificationUrl=https://<your-verifier>/v1/amazon/verify
```

Do not paste placeholders into a live build. Enabled builds require SKUs, HTTPS and both public assets. Non-production environments are rejected/disabled for normal release builds, and runtime independently rejects a non-production release environment. A dedicated non-debuggable `lat` variant is enabled only with `-PtrailnestLatBillingEnabled=true`; it pins the separate `src/lat/assets/billing-verifier-public.pem`, fixed LAT endpoint/SKUs and environment. Its `LAT_BUILD` flag is false in normal debug/release/preview builds. The current LAT packages all 19 active locales under the translation-review waiver from the owner. The normal defaults stay disabled. No RVS secret/private key belongs in Gradle, BuildConfig, APK assets or the repository. Amazon's protected response receiver and Appstore/App Tester package queries are declared in the manifest.

## Acceptance and remaining lifecycle checks

On 24 October 2026, Amazon-installed LAT 0.8.5/code 13 completed the owner-authorized free subscription checkout on KFTRWI/API 28. Premium activated only after signed verification; restore refreshed the lease and Premium survived process restart. See [core acceptance](../artifacts/amazon-lat-billing-acceptance-0.8.5.json). This supersedes the historical publication/catalog blockers below. A verification-valid-until timestamp describes the bounded signed lease, not the annual subscription's end date.

The subsequent lifecycle pass confirmed that an already-running app retained Premium after Wi-Fi was disabled. Wi-Fi was restored to its initial enabled state. Cold-start/offline restore testing was interrupted by another active tablet task and is **inconclusive**, not a pass or an app failure. Real Amazon renewal, expiry and refund/revocation events have not been executed in this pass. See [lifecycle evidence](../artifacts/amazon-lat-lifecycle-0.8.5.json).

Automated controller and verifier tests exercise continuous renewal using the same receipt, expiry, future cancellation boundaries, effective refund, subsequent verifier outage, and repeated offline restores without extending signed access. These use isolated test keys, QA identities and controlled clocks; they do not claim Amazon device lifecycle acceptance. No production receipt is fabricated or guard relaxed.

Amazon's [accelerated subscription guidance](https://developer.amazon.com/docs/app-testing/accelerated-subscriptions-introduction.html) warns that enabling acceleration cancels old subscription testing. The [timeline](https://developer.amazon.com/docs/app-testing/accelerated-subscriptions-time-table.html) maps an annual term to 30 minutes and automatically ends testing after four renewals. Obtain the owner's explicit approval before replacing the existing TrailNest test subscription; do not change unrelated apps or substitute a console reset for a genuine refund event.

The owner subsequently approved acceleration for TrailNest's existing LAT test while reserving the Kindle for Craftfolio. However, Amazon's [LAT setup guide](https://developer.amazon.com/docs/app-testing/live-app-testing-getting-started.html) states that accelerated timelines cannot be updated after the test starts, and the running Test 1 settings expose no acceleration control. No replacement track was created, no test ended and no subscription reset. A replacement LAT setup needs its own agreed scope; Kindle testing remains paused until the owner releases the tablet.

The owner then explicitly authorized a separate replacement test while keeping Test 1 unchanged. On 24 October 2026, **Test 2 — TrailNest 0.8.5 accelerated renewal LAT** was created from Test 1, with the existing code-13 APK, original device targeting and only the existing TrailNest tester group. Accelerated timelines were set to **Enable** and saved before submission; the console maps the annual period to **30 minutes**. Amazon shows Test 2 **Test submitted**, while Test 1 remains **Test in-progress** with its original release. No tablet, account or subscription reset actions were performed. Publication and all Test 2 device acceptance remain pending. See [Test 2 setup record](../artifacts/amazon-lat-accelerated-test2-setup.json).

### Historical sandbox observations

The unpublished annual subscription, Amazon public key and hosted sandbox are configured. Physical App Tester checkout, cancellation, SDK restore and real sandbox RVS calls were exercised on 23 October 2026. Successful entitlement/fulfillment acceptance is still blocked: the annual test receipt returned `term: "1 Month"` and a renewal time at the RVS request time, already past when checked. Both console `Annually` and an experimental `Annual` fixture reproduced the rejection. Auto-renew on and toggling grace did not establish a valid future horizon. No expiry bypass was added; rejected receipts remained unfulfilled and no lease was cached. This is observed sandbox behavior, not evidence of a live annual subscription. See [setup evidence](../artifacts/amazon-setup.json).

The isolated LAT service, actual shared key, independent signing key, signed LAT APK and one-member consenting tester group are configured. Core buy/restore/process-restart acceptance has passed on one Kindle. Multiple devices/accounts, interrupted callbacks, pending parent approval, real renewal/auto-renew-off/expiry/refund/grace events, cold-start offline behavior, reboot/clock changes, metadata unavailable, independent fulfillment acknowledgment/retry and price/offer changes remain separate acceptance work. Signing-key rotation/operations and production acceptance remain incomplete. Do not charge for unimplemented benefits.

The 19-locale billing text is agent-authored and active by owner instruction; human approval is not claimed and is not a release gate. Machine tests use fake store/RVS ports and explicitly marked QA UI offers. The cross-runtime crypto fixture contains only an ephemeral public key and sandbox test token with a fixed test clock; its private key is discarded. They are test evidence, not live Amazon transaction evidence. See `VERIFICATION.md` for executed results.

Primary references, checked 23 October 2026: [SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html), [3.0.9 release notes](https://developer.amazon.com/docs/appstore-sdk/release-notes.html), [IAP callbacks and manifest](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html), [RVS fields and status codes](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html), [RVS sandbox](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html), [IAP testing](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
