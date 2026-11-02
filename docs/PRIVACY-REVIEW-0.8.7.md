# Privacy engineering and operator review — 2 November 2026

Scope: paid TrailNest 0.8.7/code16 for KFTRWI, its public policy, Amazon disclosure draft and the separate Cloudflare production verifier. This is a documented technical/operator review, not a certification of legal compliance in every market.

## Operator decisions

O. Bankole / United Kingdom and the public support address were supplied by the owner. The owner explicitly approved keeping open support cases while needed, deleting/anonymising within 90 days after closure and a monthly manual review, with documented dispute/legal exceptions. No mailbox was inspected or changed and no automatic deletion is promised. The published policy names the operator, describes deletion/contact rights and links to ICO complaints. The owner remains responsible for operating the support process and handling requests.

## Data-flow review

- Journal/notes/checklist/preferences, copied photos, GPX and recording summaries stay in private app storage. The database/photo and migration device tests passed. Copied-photo GPS metadata is removed; originals remain unchanged. Backups are disabled. User deletion/clear-data/uninstall behavior is disclosed. Recorded coordinate samples are used in memory, not stored as a track.
- Optional maps make HTTPS requests only to the configured OpenFreeMap host, after the explicit Load map disclosure. IP address, app User-Agent and requested map areas/assets leave the device. No journal or photo upload is implemented. Current-position overlay is local. Shared byte/resource limits, allowed-host/redirect checks and foreground pause remain enforced.
- Optional recognition delegates to the installed Android speech service after a user action. The provider may process audio remotely. Touch alternatives remain; no TrailNest audio archive is created. Text-to-speech depends on installed voices. The owner confirmed audible clarity and the named controls' VoiceView operability on the draft.
- Amazon handles payment/card information. Only Amazon user/receipt identifiers go over HTTPS to the Cloudflare verifier and Amazon RVS for entitlement checks. There is no receipt database or application observability logging. Responses are no-store, untrusted bodies are bounded and errors redact details. IP-derived rate-limit keys protect the service. Provider infrastructure records are not claimed nonexistent or immediately erasable.
- The web policy is hosted by GitHub Pages without app analytics/tracking scripts. The policy discloses provider processing and points to provider policies. Private keys/shared secrets remain outside Git, with separate production/LAT/sandbox public pins. No production key was used for synthetic QA grants.

## Amazon questionnaire

All 39 types were answered and saved for review. Functional on-device data is included conservatively in collection answers; the policy explains its local storage. User IDs, purchase history and network identifiers include functional/security purposes. No advertising, marketing, personalization or app analytics purpose is selected. No app-created account is offered; the data-deletion link is the public policy.

The per-type third-party transfer answers apply Amazon's documented exclusions for service-provider processing and specific user-initiated/prominently disclosed actions. They do not assert that no network data is sent. Card details, contacts/phone logs, calendar, browsing history, advertising IDs and unimplemented telemetry are not claimed collected. Read-back checks after reload confirmed User IDs, purchase history, network IDs, photos and GPX/file answers.

Sources checked 2 November: [Amazon privacy labels](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html), [Amazon privacy/security policy](https://developer.amazon.com/docs/policy-center/privacy-security.html), [ICO storage limitation](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/data-protection-principles/a-guide-to-the-data-protection-principles/storage-limitation/).

## Operational limits

Support deletion/review is manual. Cloudflare/Amazon/OpenFreeMap/speech/GitHub provider records follow their policies; international processing and market-specific obligations remain the operator's responsibility. This review does not claim no provider logs, human linguistic certification, human field-route safety review or a successful real production purchase.
