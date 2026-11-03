# IAP review failure and readiness correction

Amazon rejected the 2 November code16 submission on 3 November. Primary validation passed; functionality passed on one tested device. The content-policy report identified an IAP error after Settings → Subscribe. The review video shows the localized annual price and “Purchase verification is unavailable. Restore when online.”

The shipped `HttpsReceiptVerifier.ready()` derives `/v1/amazon/ready` from the verification endpoint. The Cloudflare adapter accepted `/ready` only. Live HEAD probes returned 204 for `/ready` and 404 for the actual Android path. `BillingController.buy()` correctly refused checkout when readiness failed. The earlier release check used a separately configured health URL and missed this mismatch.

The regression command `node --test --test-name-pattern='shipped Android checkout' billing-server/tests/cloudflare.test.mjs` failed with `404 !== 204` before the fix. It derives the URL from the configured verification endpoint and binds its expression to the real Android implementation. The Worker now accepts both readiness paths under identical configuration checks, with no redirects. GET remains 405; missing configuration remains 503. Receipt verification, keys, account/package/SKU/environment/signature/date guards and the Android APK are unchanged.

Production deployment `9c52c6ad-4502-45ef-9670-f865f7bad0a4` passed both live HEAD probes (204). Invalid synthetic receipt verification remained 403 without a lease. The connected Kindle passed `BillingReadinessDeviceTest`: the actual Android HTTPS client reached readiness, and the real billing controller handed the annual SKU to a recording store double after account/product/empty-history setup. This test deliberately does not launch Amazon checkout, create a receipt, grant Premium or charge anyone. It used only the separate draft test package. Test1 and all LAT/sandbox deployments remain unchanged.

All 52 backend/tooling tests passed (28 backend, 24 tooling). The draft instrumentation APK compiled. The unchanged production APK retains SHA256 `6019e869b63e1d4ddf07b7d7d58feebb85e170553f1624f75db65846ecadf7d2`. The release gate now requires the actual Android readiness URL plus device readiness/checkout-handoff evidence, rather than an independent health URL alone.

This fixes the reproduced pre-checkout blocker. It does not establish a real paid production purchase or entitlement lifecycle; those remain unobserved. Do not describe the store double as an Amazon purchase. Amazon must review the server-corrected unchanged APK again.
