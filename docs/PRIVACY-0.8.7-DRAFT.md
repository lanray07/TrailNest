# Privacy additions for the paid 0.8.7 release — draft

Prepared 1 November 2026. The public-pages source now includes these map and conditional production-verification disclosures alongside the earlier 0.8.6 behaviour. Publish/verify that page and reconcile Amazon disclosures before enabling the paid release. This source update is not completion of the operator/legal review. Operator remains **O. Bankole**, public support **banksmi@mail.com**; the owner confirmed United Kingdom on 2 November. This does not constitute a completed legal assessment.

## Optional maps

TrailNest uses MapLibre Native to display maps from OpenFreeMap. Maps load after you choose the map-loading control. Requests disclose your IP address, app-identifying User-Agent and the map areas/assets requested to OpenFreeMap and its CDN provider Cloudflare. The viewed area can reveal an approximate place of interest or route location. Your journal, photos and notes are not included in those map requests. Current position overlays are computed locally; moving/viewing the map can request the corresponding map area.

Downloaded map regions, styles, glyphs and sprites are stored in the app's private map database. Deleting a saved region removes its offline association; shared/ambient cached resources may remain. Clearing app data or uninstalling removes private app storage. Revoke optional location permission or avoid loading maps to prevent those respective operations. Map attribution and third-party terms remain available in the app.

Sources: [OpenFreeMap privacy](https://openfreemap.org/privacy/), [OpenFreeMap terms](https://openfreemap.org/tos/), [Cloudflare privacy](https://www.cloudflare.com/privacypolicy/).

## Annual Amazon subscription

When production purchasing is configured and enabled, Amazon processes checkout, payment, renewal and cancellation under its own terms. TrailNest does not collect card details. The Amazon SDK supplies an Amazon user identifier and receipt identifier to the app. TrailNest sends those identifiers over HTTPS to its Cloudflare-hosted verification service, which sends them to Amazon RVS to verify the annual product and current receipt status.

The verifier returns an expiring signed lease bound to hashed account/receipt identifiers, package and product. The app stores its billing state and lease privately to support bounded offline access and restore. The verifier has no receipt database and does not intentionally log receipt identifiers, request bodies, keys or RVS URLs. Application observability is disabled. Cloudflare and Amazon can process infrastructure/security metadata under their own policies; this is not a promise that those providers retain no logs. Cloudflare's processing and any international transfers require the operator's privacy assessment.

Removing the app's local data does not cancel an Amazon subscription. Manage or cancel it through Amazon. Restore when online to refresh entitlement; expired or revoked receipts cannot grant new access. The app's maximum six-hour lease is distinct from Amazon's subscription period.

## Outstanding operator decisions and disclosures

- Operator identity/contact and country are recorded: O. Bankole, United Kingdom, banksmi@mail.com. Review any additional market-specific information obligations before release.
- Establish and operate a support-correspondence retention/deletion process; the previous policy does not specify a fixed period. Do not promise an automated deletion schedule that is not implemented.
- Assess provider roles, lawful basis, transfers and rights handling for the intended markets. Do not claim legal compliance from this draft.
- Update Amazon's questionnaire for external map-area/IP requests and receipt/account verification. Preserve accurate local processing, optional voice-service and recording explanations from the existing policy.
