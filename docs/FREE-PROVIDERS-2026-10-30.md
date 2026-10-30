# Free providers for the paid TrailNest product

Checked 30 October 2026 against primary sources. This is a provider assessment, not evidence of an implemented map renderer, published route catalogue, deployed commercial verifier or completed legal review. No account, purchase, deployment, subscription reset or LAT change was made for this research.

## Recommended stack

| Requirement | Candidate | Decision and limits |
| --- | --- | --- |
| Hosted worldwide basemap | OpenFreeMap + MapLibre Native | Best initial candidate. Commercial use is allowed; its public instance needs no account or API key. Attribution remains required. There is no availability SLA. [Provider FAQ](https://openfreemap.org/) |
| Small offline map areas | OpenFreeMap | Its terms, updated 14 October 2026, permit downloads of at most 3,000 tiles **or** 50 MB, with at most four concurrent requests and an app-identifying User-Agent. Conservatively enforce both size and tile ceilings, across all source requests, rather than treating either ceiling as a bypass. Larger downloads need separate permission. [Terms](https://openfreemap.org/tos/) |
| Renderer/style integration | MapLibre Native, OpenFreeMap Liberty style | The provider explicitly supports mobile MapLibre Native and documents `https://tiles.openfreemap.org/styles/liberty`. Verify Fire-device rendering, lifecycle, offline assets and attribution in the actual APK. [Mobile integration guide](https://openfreemap.org/quick_start/) |
| Own offline distribution later | Regional Protomaps PMTiles extracts | Open alternative when provider download limits become insufficient. Basemaps are ODbL Produced Works with OSM attribution; a planet file is about 120 GB. Regional extracts are supported. Copy extracts to owned storage: the provider discourages hotlinking its download URLs. Hosting and operational costs are not automatically zero. [Basemap downloads](https://docs.protomaps.com/basemaps/downloads) |
| Global route-data starting point | OpenStreetMap route relations | Free licensed data, not a verified hiking guide. Credit OSM contributors and the ODbL; public use of an adapted database can require making that database available under ODbL. Preserve provenance separately from TrailNest's editorial text. [OSM copyright](https://www.openstreetmap.org/copyright), [ODbL summary and full-text link](https://opendatacommons.org/licenses/odbl/summary/) |
| Authoritative regional trail data | California State Parks Recreational Routes | Useful first regional source. The state catalogue marks it Creative Commons Attribution and records no restrictions on public use. Data describes route locations, names and park attributes, current February 2026; catalogue updated 15 October 2026. The catalogue's generic CC-BY link does not unambiguously pin a licence version: preserve publisher metadata and resolve that before redistribution. [Official dataset](https://publish.data.ca.gov/dataset/recreational-routes) |
| Commercial receipt verifier | Cloudflare Workers Free, subject to runtime acceptance | A credible free-hosting candidate, not yet approved for this verifier. Free includes 100,000 requests/day and 10 ms CPU per invocation; exceeding a free quota can make verification unavailable. Benchmark RSA signing/key import and cold starts in the deployed runtime before enabling paid checkout. [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/) |

## Maps: what to enforce before enabling downloads

Use a single shared downloader/concurrency budget for the whole map-pack operation. Preflight its tile pyramid, reject oversized areas, impose streaming byte limits, cancel incomplete downloads safely and do not claim a partial pack is available offline. Include styles, fonts and sprites in offline acceptance, rather than testing tile geometry alone. These are implementation recommendations inferred from the provider's limits, not features already implemented.

OpenFreeMap's project licence is MIT, while its included data, styles and fonts have distinct licences: OpenMapTiles/style design includes attribution licences; OSM data is separately licensed; Noto Sans uses SIL OFL. Retain the applicable notices and visible map attribution instead of describing the whole stack simply as MIT. [Provider licence inventory](https://github.com/hyperknot/openfreemap/blob/main/LICENSE.md)

Do **not** use `tile.openstreetmap.org` as the offline download source: its own tile policy explicitly prohibits offline/prefetch/bulk downloads. OSM's data licence does not grant unlimited use of that hosted tile service. [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/)

MapTiler's Free plan is for testing, personal or non-commercial use, so it is unsuitable for the intended paid product without another arrangement. [MapTiler pricing](https://www.maptiler.com/cloud/pricing/)

## Route content: rights are available; editorial acceptance is still work

Waymarked Trails is a useful discovery/reference source because its own project describes displaying recreational routes from OpenStreetMap. Its website software is GPL-3.0; that software licence must not be confused with the underlying OSM data licence. No unrestricted bulk-use commitment for its public API was established in this review. Use licensed OSM data or publisher downloads for a reproducible catalogue rather than assuming a public GPX/API endpoint is a production backend. [Waymarked Trails project](https://github.com/waymarkedtrails/waymarkedtrails-website)

California's official layer supplies polyline geometry through REST and supports GeoJSON queries, with a 2,000-record response limit. It is trail data, not automatically a set of complete continuous itineraries; retain identifiers and handle pagination, discontinuities and permitted-use attributes. [Publisher's feature layer](https://services2.arcgis.com/AhxrK3F6WM8ECvDi/arcgis/rest/services/RecreationalRoutes/FeatureServer/0)

An open licence allows reuse; it does not establish current public access, suitability, difficulty, closures, hazard advice or a reviewed start-to-finish walk. This is an engineering/editorial inference, not a claim made by a licence. Each shipped route still needs source/licence/attribution, geometry validation, current access/closure checks, a named reviewer, review date and the repository's explicit human approval. Do not set `humanApproved` from an automated import or merely from government provenance.

The UK government's Epping Forest Waymark Trails listing is **not** a shortcut to an assumed OGL licence: it explicitly points to an Ordnance Survey INSPIRE licence, despite the portal's generic OGL footer. The dataset-specific rights must be resolved before commercial reuse. [Dataset-specific licence information](https://www.data.gov.uk/dataset/5d776350-5fc6-40e4-9659-61595c17b7bd/waymark-trails1)

## Free commercial billing hosting

Cloudflare's reviewed Self-Serve Agreement includes Free Services and contains no Vercel-style personal/non-commercial-only restriction. It does prohibit processing or collecting personal/business credit-card information on Free Services. **Inference:** an Amazon receipt-verification endpoint, receiving receipt/account identifiers but no card information, is a plausible commercial Free use case. This is not a provider approval or legal certification; Amazon and Cloudflare terms and privacy duties still apply. [Cloudflare agreement, sections 2.2 and 2.6](https://www.cloudflare.com/terms/)

Workers documents `node:crypto` support for hashing, signing, key operations and verification. The listed exceptions do not exclude the verifier's RSA/SHA-256 use. Current compatibility dates at or after 4 August 2026 enable Node compatibility by default. A separate Worker HTTP adapter and actual runtime tests are still needed; Node unit-test success alone is insufficient. [Node crypto compatibility](https://developers.cloudflare.com/workers/runtime-apis/nodejs/crypto/)

The Workers rate-limit binding is GA and recommended for production, and the runtime docs require Wrangler 4.36.0 or later. The reviewed primary pages do not state a paid-only restriction or separate per-call charge, but they also do not explicitly guarantee Free-account entitlement. Verify binding provisioning on the intended Free account. Counters are approximate and local to each Cloudflare location; they are not a strict global or per-instance quota. For a strict global upstream quota, Cloudflare recommends a Durable Object. Fail closed if required protection is missing; do not replace it with an unverified environment flag. [GA notice](https://developers.cloudflare.com/changelog/post/2025-09-19-ratelimit-workers-ga/), [binding configuration, locality and accuracy](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/)

Deployment requires a Cloudflare account, supported Node/Wrangler tooling and authenticated access to the intended account. Use a separate production Worker, secret storage for a new production signing key and the real Amazon shared key, and a production Android public pin. Do not copy the LAT identity or weaken test-receipt rejection. Account creation or a cached Wrangler directory does not prove deploy access. [Getting started](https://developers.cloudflare.com/workers/get-started/guide/), [Workers secrets](https://developers.cloudflare.com/workers/configuration/secrets/)

The existing Vercel team was independently observed by the main task as Hobby. Its official plan restricts use to personal, non-commercial projects. Leave the sandbox/LAT projects intact; do not silently upgrade the account or make a commercial release depend on Hobby. [Vercel Hobby plan](https://vercel.com/docs/plans/hobby)

## Remaining release work

1. Integrate and test the real basemap and bounded offline downloads on the selected Fire hardware, including attribution, interrupted downloads and offline restart.
2. Import a small licensed route set as drafts, then obtain the required human editorial/access review before publishing it.
3. Validate Cloudflare Free account access, rate-limit binding, RSA CPU budget and production secrets before selecting it as the live verifier host.
4. Rebuild the paid production APK with the distinct verifier URL/pin; verify restore/denial behaviour and accurate subscription benefits.
5. Update privacy disclosures, localized listings and real screenshots to match the resulting product, then rerun the release gates. Free-provider research does not close these gates by itself.
