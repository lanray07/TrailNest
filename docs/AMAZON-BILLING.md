# Amazon billing integration — prepared, disabled

Android 0.8 integrates the native Amazon Appstore SDK **3.0.9** and a separate Node receipt-verification service. **No Amazon app products, public authentication key, RVS shared secret, hosted verifier or live acceptance results were supplied. Billing is disabled by default, no prices are invented, and no real purchase or refund is claimed as tested.** The Amazon release gate remains unapproved. Optional one-time packs, trial/promotional offers, add-ons and tier changes are not implemented; unsupported offers are not sold as a simple annual plan.

## Purchase and restore flow

The application registers its SDK listener on the main thread only when billing is explicitly configured. Resume/Restore confirms the current Amazon IAP user, fetches the actual annual term's product metadata and pages through purchase updates with a full reset. Only a subscription term reported as `Annual`, with a non-empty Amazon price and no unhandled promotion/trial, enables checkout. Amazon handles its checkout and cancellation/failure UI. Pending approvals do not grant or fulfill anything. A backend readiness request must succeed before a new checkout starts.

Receipt IDs and Amazon IAP user IDs go over HTTPS to `/v1/amazon/verify`. The backend chooses its own fixed Amazon RVS endpoint/environment and allowed parent/annual SKUs; the request cannot supply price, expiry, SKU, sandbox mode or an entitlement flag. RVS success is checked against receipt ID, product, term, type, dates and test flags. Revoked/invalid receipts are denied; throttling, credential errors and service outages remain retryable. Grace can extend a past renewal; a refund/cancellation effective now cannot be overridden by grace. Disabling auto-renew does not by itself revoke a currently active receipt.

The backend returns a compact RSA-SHA256 signed lease, not an authority-bearing JSON boolean. Its payload binds the application ID, environment, SHA-256 account/receipt identifiers, parent SKU, annual SKU, issue time and expiry. Android checks the pinned public key and every binding. The lease lasts **at most six hours**, capped by relevant cancellation/renewal/grace dates. A valid RVS receipt without a future renewal date gets only that short revalidation lease, never perpetual access. Tokens for sandbox, LAT, another account, another receipt, another SKU or another app cannot unlock production.

A verified lease must be durably saved before notifying Amazon `FULFILLED`. Replayed receipts are idempotently verified and acknowledged again. Stale request IDs, older verification batches and account switches cannot write or fulfill. Restore aggregates pages so an old canceled receipt does not overwrite a newer valid purchase. Recent checkout receipts are retained in memory to bridge a resume/history race and are still reverified with RVS. No consumable inventory is implemented.

Offline access requires a current SDK-confirmed Amazon account and a valid signed cache. Monotonic elapsed time, wall time and boot count invalidate reboot/rollback/clock-change reuse; where boot count is unavailable a per-process nonce requires fresh verification after process restart. The UI refreshes its local deadline periodically and each pack-limit decision checks the lease again. A definitive denial suppresses the cache in memory even if disk clearing fails. Offline revocation can lag by the remaining lease (at most six hours); this is not real-time revocation. The server is stateless and does not yet consume real-time notifications or provide a persistent subscription ledger.

Premium currently lifts the **private GPX geometry-pack limit only**. It does not create map licences, enable unavailable maps/curated routes or promise unfinished Premium features. Existing free checklist/journal/import/export behavior remains available. No purchase control appears in the default unconfigured panel.

## Backend deployment configuration

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

Do not paste placeholders into a live build. Enabled builds require SKUs, HTTPS and both public assets. Non-production environments are rejected/disabled for release builds, and runtime independently rejects a non-production release environment. The normal defaults stay disabled. No RVS secret/private key belongs in Gradle, BuildConfig, APK assets or the repository. Amazon's protected response receiver and Appstore/App Tester package queries are declared in the manifest.

## Acceptance still required

Create the real annual subscription and test locale/price metadata in the console. Configure the server, signing-key rotation/operations and Amazon app public key. Use App Tester with the documented SDK sandbox-mode setup and RVS sandbox, then Live App Testing against production RVS in a separate LAT configuration. Record successful buy/restore on multiple devices/accounts, interrupted callbacks, pending parent approval, renewal, auto-renew off, expiry, refund, grace, outages, reboot/clock changes, metadata unavailable, fulfillment retry and price/offer changes. Verify actual billing benefits and clear cancellation/renewal wording. Do not charge for unimplemented benefits. No configured App Tester/LAT purchase was made in this task.

The new 19-locale billing text is agent-authored and **all entries remain drafts** pending human linguistic/commercial-terms/accessibility review. Machine tests use fake store/RVS ports and explicitly marked QA UI offers. The cross-runtime crypto fixture contains only an ephemeral public key and sandbox test token with a fixed test clock; its private key is discarded. They are test evidence, not live Amazon transaction evidence. See `VERIFICATION.md` for executed results.

Primary references, checked 23 October 2026: [SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html), [3.0.9 release notes](https://developer.amazon.com/docs/appstore-sdk/release-notes.html), [IAP callbacks and manifest](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html), [RVS fields and status codes](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html), [RVS sandbox](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html), [IAP testing](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
