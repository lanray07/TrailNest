# Submission acceptance — TrailNest 0.8.7 / code 16

**3 November update:** Amazon rejected the initial submission for an IAP error. The Android checkout readiness URL was missing from the production Worker. This server routing defect was reproduced, corrected and verified on the Kindle, with a regression test and passing GitHub checks. The unchanged APK is resubmitted and Amazon confirms **SUBMITTED**; estimate 8 November, 08:30 PST. See `IAP-REJECTION-2026-11-03.md`. The original acceptance below missed that URL contract; it is not evidence of a real new-SKU purchase.

The signed paid candidate was submitted to Amazon on 2 November 2026. The Console confirms **SUBMITTED** and “App Submission Successful”; its estimated Appstore date is 7 November 2026, 07:00 PST, not an approval guarantee. The dedicated annual IAP is Approved. Test1 is unchanged.

Final [GitHub App checks](https://github.com/lanray07/TrailNest/actions/runs/41462840481) passed for source commit `f4e3405`. Sanitized submission evidence: `artifacts/amazon-production-submission-2026-11-02.json`.

| Area | Accepted evidence | Explicit scope/limit |
| --- | --- | --- |
| Device | 13 core device tests, 88 locale/width/font layouts, 26 billing layouts, 10 real-keyboard cases | Fire HD10(2021) KFTRWI/API28 only |
| Offline maps | Online/full cold-offline render after ambient-cache clearing; cancel/resume with one region | Saved initial route viewport; no arbitrary panning or country-wide certification |
| Routes | Two continuous licensed OSM Tamsin Trail sections, owner-authorized AI desk review and dated access sources | Not field checked; no reviewed turn/safety/accessibility certification |
| Billing | Actual compiled production bindings/pin/signatures, real historical LAT lifecycle, unchanged Android billing engine/receipt guards, new-SKU loader tests, deployed negative paths and QA signing/CPU checks | Pre-release qualification only; new-SKU real charged purchase remains a post-publication check |
| VoiceView | Owner reported clear speech and usability of navigation, route/map controls, checklist states, language picker, journal fields and Restore control | Owner-reported scoped human review; not every language/device/action |
| Privacy | Owner identity/country, explicitly approved 90-day/manual monthly retention, technical data-flow review and all39 Amazon questionnaire fields saved/read back | No automatic mailbox deletion or blanket legal-compliance certification |
| Naming | Same-name Apple app identified; owner explicitly kept TrailNest for Amazon | No trademark clearance claimed |

`config/release-scope.json` names the two route IDs and KFTRWI. GB coverage is **supported_limited**, not country-wide. Other reviewed country coverage remains planned; distribution of a private journal/GPX tool in other markets does not certify local routes or provider coverage there.

The gate now validates matching version/code, publishable scoped routes and actual offline/device evidence. Billing acceptance checks source hashes, compiled APK bindings and uploaded hash, real historical LAT evidence, new production contract/negative paths and two deployed QA signature/CPU samples. A true bookkeeping flag without the corresponding evidence does not pass. Receipt/signature/expiry/account/package/annual-SKU guards are unchanged.

The optional async signing dispatch and Cloudflare Web Crypto implementation passed RSA-SHA256 compatibility tests and two fresh deployed QA probes. Production Worker `5db98878-d262-427a-b52e-755d0d59ddde` is ready204 and rejects an invalid receipt403 without a lease. QA used an ephemeral key/draft audience, never the production key, and the temporary Worker is confirmed absent.

Public [privacy](https://lanray07.github.io/TrailNest/privacy/) and [support](https://lanray07.github.io/TrailNest/support/) documents are published. The owner explicitly certified and authorized Amazon's export declaration for this unchanged code16 candidate. No new purchase, tester invitation, reset or Test1 change is authorized or performed by this record.

After publication, the owner-controlled production checkout/Restore/lifecycle smoke remains unverified until actually observed. See `docs/BILLING-PRE-RELEASE-0.8.7.md`; do not relabel historical LAT or synthetic QA as a real production transaction.
