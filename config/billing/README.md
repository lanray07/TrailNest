# Amazon sandbox configuration

Created 23 October 2026 for the unpublished TrailNest app.

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

Physical App Tester purchase/restore callbacks reached the hosted verifier, but entitlement acceptance remains blocked by the sandbox response's past renewal horizon and `1 Month` term for an annual purchase. The verifier rejected these receipts and issued no lease. The temporary `Annual` spelling experiment was not adopted in this fixture. See [actual observations](../../artifacts/amazon-setup.json). No LAT or live purchase has passed.

`sandbox-verifier-public.pem` is the actual sandbox signing public key. Its private counterpart was generated in memory, stored directly as a Vercel Secret and discarded locally. It is not a production key. Android's current `billing-verifier-public.pem` pins this sandbox key. Replace it with the live verifier's public key before any production billing build; retain the default disabled build setting until live acceptance passes.

The LAT project `trailnest-billing-lat` (`prj_77R1enZh8tGps7njlv5W509qzL2B`) exists but is not yet configured/deployed. It must use its own signing key, actual Amazon RVS shared key, `lat` environment, matching signed APK and separate endpoint. It must not reuse this sandbox key or test shared-secret value. See [setup guide](../../docs/AMAZON-SETUP.md).
