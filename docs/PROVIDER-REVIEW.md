# Provider and distribution decisions

Checked 23 October 2026. No commercial map or route-content rights are available; confirmed by the user during this build. No provider is enabled and no network tiles are requested or downloaded.

## MapLibre Native candidate

MapLibre Native offers an Android renderer under BSD-2-Clause. It is a renderer, not a licence for map data, styles, fonts, imagery or offline distribution. Its Android SDK and offline APIs are plausible candidates, but Fire OS support and renderer performance must be tested on the specific intended Fire devices before selection. We have not integrated or certified this SDK. The current geometry canvas provides no basemap.

Sources: [MapLibre Native](https://github.com/maplibre/maplibre-native), [Android licence](https://github.com/maplibre/maplibre-native/blob/main/platform/android/LICENSE.md).

## Ordnance Survey

The OS Maps API is a candidate for UK online mapping. Available styles, API access and caching/offline terms must be checked against the actual account and signed contract. No right to download tiles is inferred from renderer support or the presence of a public API. `TilePermission.mayDownload` requires both a positive permission and contract reference. No public community tile servers are used.

Source: [OS Maps API](https://docs.os.uk/os-apis/accessing-os-apis/os-maps-api).

## Amazon distribution and purchasing

Amazon discontinued its Appstore for Android mobile devices on 20 August 2025. The primary release target remains Fire tablets. A generic Android APK can still be produced, but Android-phone Amazon distribution must not be advertised.

For purchasing, use the current Amazon Appstore SDK, actual console-configured products, and a server-side Receipt Verification Service integration before granting entitlement. Cancellation, expiry, refunds and offline caching need integration tests with App Tester and Live App Testing. Android 0.8 includes the native 3.0.9 adapter, RVS server and signed lease checks, but the default unconfigured build remains disabled and cannot grant Premium. Actual products, public authentication key, hosted server/secrets and App Tester/LAT acceptance are still missing. It contains no live Amazon credentials or simulated live purchase success. See `AMAZON-BILLING.md`.

Sources: [distribution change](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program), [SDK migration](https://developer.amazon.com/docs/in-app-purchasing/transitioning-to-appstore-sdk.html), [IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html).

## Localisation and listing limits

`config/locales.json` records the official listing locales separately from requested in-app targets. In the checked documentation, English UK and Spanish Mexico are device/in-app targets but are not separate storefront localizations. The broader requested targets remain in the registry with `amazonListing: false`.

Short descriptions: 2,000 bytes. Long descriptions: 4,000 characters. Feature bullets: three to five. Display-title availability and its console-enforced maximum have not been verified. The development listing does not advertise unimplemented offline maps, turn directions or a live catalogue.

Sources: [international distribution](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html), [Appstore details](https://developer.amazon.com/docs/app-submission/appstore-details.html).

## Access guidance

The linked Countryside Code applies to England and Wales; the source links separate guidance for Scotland and Northern Ireland. No worldwide right-of-way policy is inferred. Emergency numbers are intentionally absent until a country-specific source and review are recorded.

Source: [Countryside Code](https://www.gov.uk/government/publications/the-countryside-code).
