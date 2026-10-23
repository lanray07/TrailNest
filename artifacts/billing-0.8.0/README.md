# Android 0.8 billing evidence

The native Amazon 3.0.9 adapter, RVS backend and signed six-hour entitlement lease are implemented, but every shipped build in this run has billing disabled. Real products, public key, server secrets/hosting and App Tester/LAT acceptance are missing. No real Amazon purchase, refund or RVS verification was executed. No production billing approval is granted.

60 JVM tests include 25 billing state/crypto tests. Nine server tests use mock RVS replies and a real local HTTP boundary. Nine tooling and five website tests also pass. Fourteen instrumented tests ran on Fire KFTRWI/API 28, including Android verification of a Node-signed sandbox-only fixed-clock fixture and the protected SDK receiver manifest/default-disabled runtime. The fixture's private key was generated in memory and discarded; only a test public key/token is committed under androidTest.

The billing panel fixture covers all 19 locales at 360 dp/200% text plus Arabic/German/Japanese/Hindi at 800 dp/200%: 23 configurations × seven states = 161 states. Labels, wrapping, enabled/disabled actions and UI callbacks pass; 115 target audits contain 184 repeated action observations. The explicit QA PRICE offer and active-state fixtures are not live product/entitlement evidence. The general 76-case layout matrix and 76-case real-keyboard matrix also pass.

Normal app data, system locale and font settings were untouched. Tests use the isolated draft package, temporary windows above the PIN lock and the default MaterialTheme. Human translation/commercial-terms/accessibility review and live Amazon acceptance remain pending. See ../../docs/AMAZON-BILLING.md and ../../docs/VERIFICATION.md.
