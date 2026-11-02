# Billing pre-release qualification — 2 November 2026

Candidate: signed paid 0.8.7/code16. Production annual SKU `com.trailnest.premium.production.annual`, parent `com.trailnest.premium.production`, GBP4.99 base/year, no trial/promotion, Amazon status Approved.

This qualifies configuration and tested implementation for submission; it does **not** claim a real new-SKU production purchase, production receipt grant or renewal has occurred.

## Evidence

- Actual APK DEX inspection confirms production environment, enabled purchasing, correct package/SKUs/HTTPS endpoint, disabled LAT/debug flags and the retained independent production verifier pin. The draft APK is correctly rejected. Retained v2/v3 app signatures verify; APK SHA256 is `6019e869b63e1d4ddf07b7d7d58feebb85e170553f1624f75db65846ecadf7d2`.
- Test2's real 27 October purchase, fulfillment, four renewals and RVS expiry are recorded. The 29 October expired-state online Restore, cold offline restart, offline Restore and reconnect checks passed. The event-time device latency was not observed. These are historical old-SKU/build results, not relabelled as new-SKU transactions.
- Android billing source is unchanged from that real LAT baseline. Receipt guard logic is unchanged. The optional async signing dispatch and Cloudflare Web Crypto signer were separately tested with the existing RSA-SHA256 contract. The default Node signer remains for other hosts; existing LAT/sandbox deployments were not changed.
- 27 backend tests, 24 tooling tests, 13 scoped core device tests and 26 billing layout configurations pass. Production loader tests grant only the configured new annual SKU and reject old/test/beta receipts. Account/receipt/package/SKU/signature/time guards remain intact.
- The real deployed adapter/verifier signed sixteen synthetic responses across two fresh Web Crypto QA deployments, using an ephemeral key and draft audience. All signatures verified. Request CPU p99 was 8,436µs and 7,592µs, versus 12,448µs for the old synchronous cold path. Startup key preparation alone did not fix the spike and was not adopted. These small samples are not an all-location/load guarantee.
- Production Worker version `5db98878-d262-427a-b52e-755d0d59ddde` returns readiness204 and invalid-receipt403 with no lease. The production pin/secrets are retained. No synthetic production entitlement was issued. The QA Worker/key were removed; API absence returned404. Wrangler's unrelated KV-enumeration permission error required no permission expansion.

## Test-stage boundary

Amazon documents sandbox and LAT testing before publication, with charged production purchases and production receipt checks after publication: [IAP testing workflow](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html). The acceptance record therefore states the pre-release qualification separately from the pending customer-mode smoke check. No production/test flag is relaxed to turn a LAT receipt into a production grant, and no new purchase or reset is used to manufacture evidence.

After Amazon makes the app/IAP Live, the owner should complete an explicitly approved paid checkout, verify the displayed price and signed Premium state, online Restore, cold offline retention within the lease, and later cancellation/expiry behavior. Keep identifiers and raw authentication/receipt logs out of Git/chat. A failed post-publication check must be investigated; it must not be reported as passed from the pre-release record.

Records: `artifacts/billing-pre-release-acceptance-0.8.7.json`, `artifacts/cloudflare-signing-cpu-2026-11-02.json`, `artifacts/billing-source-continuity-0.8.7.json` and `artifacts/production-apk-0.8.7.json`.
