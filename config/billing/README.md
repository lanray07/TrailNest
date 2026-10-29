# Amazon sandbox configuration

Created 23 October 2026 for the unpublished TrailNest app. Current review: 29 October 2026.

Test 2 on Amazon-installed 0.8.5-lat/code 13 has passed a new purchase, signed verification/fulfillment, four accelerated renewals, RVS-side expiry, and final expired-state online Restore/cold offline restart checks. Original event-time device revocation latency remains unobserved. These results supersede the historical setup blockers below, but do not approve production billing or the complete device suite. See [current release review](../../docs/AMAZON-RELEASE-REVIEW.md) and [billing evidence](../../docs/AMAZON-BILLING.md).

- Parent SKU: `com.trailnest.premium`
- Annual term: `com.trailnest.premium_term`
- Console base price: GBP 4.99 annually; no trial/promotion. Amazon calculated US price: USD 6.69.
- Support email supplied by the owner: `banksmi@mail.com`.
- Amazon app ID: `amzn1.devportal.mobileapp.a5a90d52147643eeaca74c16ac6f8d94`.
- Sandbox package: `com.trailnest.app.draft`.
- Sandbox verifier: `https://trailnest-billing-sandbox.vercel.app/v1/amazon/verify`.
- Vercel project: `prj_Wgtw1xdMEdMiHAJ9lcWOd9MvnYLp`, team `team_A8ItegmhcsA98pcqFKDl5VoS`.
- The sandbox project's Production URL is public by owner instruction; Preview URLs remain protected. This is Vercel's deployment target name, not Amazon production billing.
- A WAF rule limits all POST requests to 20/minute/IP. The process limit is additional, per instance only.

`console-export.json` is Amazon's raw catalog export. It unexpectedly contains a zero US currency price, despite the saved console price of USD 6.69. `amazon.sdktester.json` is a **derived US-only App Tester fixture** with the observed USD 6.69 price and the invalid empty icon URL removed. It is not live price evidence. The app still gets its price from the Amazon SDK, not these repository files. Review a fresh export before testing other marketplaces or going live.

Physical App Tester purchase/restore callbacks reached the hosted verifier, but entitlement acceptance remains blocked by the sandbox response's past renewal horizon and `1 Month` term for an annual purchase. The verifier rejected these receipts and issued no lease. The temporary `Annual` spelling experiment was not adopted in this fixture. See [actual observations](../../artifacts/amazon-setup.json). At that historical sandbox stage no LAT purchase had passed; subsequent LAT results are summarized above. Production purchases remain unverified.

`sandbox-verifier-public.pem` is the actual sandbox signing public key. Its private counterpart was generated in memory, stored directly as a Vercel Secret and discarded locally. It is not a production key. Android's current `billing-verifier-public.pem` pins this sandbox key. Replace it with the live verifier's public key before any production billing build; retain the default disabled build setting until live acceptance passes.

The LAT project `trailnest-billing-lat` (`prj_77R1enZh8tGps7njlv5W509qzL2B`) is separately configured at `https://trailnest-billing-lat.vercel.app/v1/amazon/verify`. It uses the actual Amazon RVS shared key (transferred directly into Vercel Secret storage by explicit owner approval), an independently generated RSA signing key and `AMAZON_RVS_ENV=lat` / `BILLING_APPLICATION_ID=com.trailnest.app`. The private key never leaves Vercel; `lat-verifier-public.pem` is the public pin, overlaid into the dedicated LAT APK from `app/src/lat/assets/`. Neither sandbox key nor sandbox shared-secret value is reused. Production service/build configuration is still separate and pending.

The original signed `0.8.0-lat` APK was non-debuggable, English-only and uploaded to Amazon LAT release `amzn1.devportal.apprelease.7731a438b4bf42ec8d2a3abc8b6071a0`. The annual IAP is approved for TEST only. A single consenting tester is registered in the `TrailNest annual billing LAT` group; no tester address is stored in Git. Test execution and submission status are recorded separately in [LAT evidence](../../artifacts/amazon-lat-setup.json). See [setup guide](../../docs/AMAZON-SETUP.md).
