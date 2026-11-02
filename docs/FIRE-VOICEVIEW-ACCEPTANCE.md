# Human VoiceView check — TrailNest 0.8.7/code 16

Device: Fire HD 10 (2021), KFTRWI. Use only the separate TrailNest draft (`com.trailnest.app.draft`), not the Amazon-installed LAT app. Do not buy, reset subscriptions, clear app data or change Test 1. Automated layout/a11y checks have passed; this check records what a person actually hears and can operate.

1. Note whether VoiceView was initially on or off. In Fire Settings > Accessibility, enable VoiceView if needed. Use the installed voice; do not enter credentials or install services.
2. Open the TrailNest draft. Swipe through Discover, My walks, Offline packs, Checklist and Settings. Each destination should have a meaningful spoken label and current selection; there must be no focus trap.
3. Open a Tamsin Trail section. Read its title, AI desk-review/not-field-checked warning and map-load disclosure. Map controls and Back must be named. Geometry/map graphics must not be the only source of essential route information.
4. Open Checklist. Find an item by speech, toggle it, and confirm the new checked state is spoken. Restore its original checked state.
5. In Settings, find App language, units, theme, spoken-output availability and Premium/Restore purchases. Controls should be distinguishable and disabled/unavailable billing should be understandable. Do not make a purchase.
6. Open the app-language picker, navigate its choices and dismiss without changing the selection. Verify focus returns to a useful place.
7. In My walks, inspect recording controls and the journal editor without starting a real walk or saving personal data. Focus the title/notes fields; confirm their labels and keyboard dismissal remain usable. Cancel editing.
8. Check an error or unavailable state if safely available. Its explanation should be reachable by VoiceView and dismissible. Do not submit private information to a speech service.
9. Check that no essential control is unlabeled, unreachable, obscured or announced in an unusable order. Report the exact screen/control and what was spoken for any failure.
10. Restore VoiceView to its initial state if you changed it. Report PASS/FAIL per step, your review date and that you actually listened with VoiceView. This is not a field-route or translation certification.

Record results in `artifacts/voiceview-human-0.8.7.json` only after the person reports observations. An agent run, automated semantics audit or checklist creation must not be marked human-reviewed.
