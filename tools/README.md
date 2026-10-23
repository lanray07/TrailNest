# Development tooling

`node tools/localise.mjs` extracts existing Android resource strings and reports missing translations, review state, glossary/placeholder errors and remaining hardcoded Compose text. `--release` fails while localisation is incomplete. It does not falsely generate translated locales by copying English.

To draft non-critical translations, use `--translate=de` (or another registry tag). Supply development-only environment variables `TRAILNEST_TRANSLATION_URL` (HTTPS) and `TRAILNEST_TRANSLATION_TOKEN`. The configurable service accepts `{source,target,key,text,protectedTerms,terminology}` and returns `{translatedText}`. Drafts are cached by endpoint, content and glossary; token values are never written. No API calls were made during this build. Human review requires `reviewed`, `reviewer` and `reviewedAt` per entry and locale approval in the registry. Safety-critical keys are excluded from automation. Plural extraction, plural-category validation, full source migration and screenshot localisation remain pending; the release gate fails accordingly.

`node tools/editorial.mjs validate` checks the empty initial catalogue or future route records. It rejects published records without a named reviewer, review date, explicit human approval, source, licence and valid geometry. `retire` records the operator and reason in revision history. This is local validation/retirement tooling, not a hosted editorial CMS or partner authorization system.

`tools/capture-android.ps1` captures real app screens from a connected development device. It refuses to label captures as marketing-ready. No route examples are seeded. A physical Fire device or working emulator is needed.
