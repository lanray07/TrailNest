# Background walk recording (Android 0.3)

Open **My walks → Start live recording**. Grant location access and optionally notification access. Approximate location is accepted as a permission, but fixes worse than 40 metres are excluded from distance. No map or route provider is used. A device with no enabled location provider cannot start.

The non-exported `location` foreground service owns Android LocationManager subscriptions independently of the activity. Recording continues when the activity is minimised, the screen is locked, or its task is swiped away, subject to OS/device restrictions. Its ongoing notification offers Pause and opens My walks for the other controls. If Android notification permission is denied, foreground-service recording can still run; reopen My walks for controls. Starting/resuming is always initiated from the visible activity. There is no background-location permission, boot receiver, automatic restart, tracking server or network upload.

**Pause recording** stops location subscriptions and active time. **Resume recording** starts a new distance segment. **Finish and review** pauses before displaying title/notes and Save. Save inserts the journal entry using the session's stable ID and `INSERT IGNORE`, then removes the draft. Retrying after an interruption between these operations cannot duplicate the entry or overwrite later journal edits. A save failure keeps the draft. **Discard recording** requires confirmation and removes the draft without a journal entry.

Active duration uses elapsed realtime, including screen-off time, excluding pauses. A small private preference checkpoint stores session ID, start date, accumulated duration and estimated distance on accepted fixes and a nominal five-second handler tick. Coordinates exist only in memory and are discarded, so recorded tracks cannot be exported or replayed. No database schema change is needed; the existing Room 1→2 migration remains intact.

After process death, a reboot, Android's app Stop action or force-stop, a persisted draft opens **paused** and requires explicit resume. Time/distance after the last durable checkpoint can be missing. Five seconds is a scheduling target, not a maximum loss guarantee during device sleep or OS suspension. Recording does not restart itself. Save/discard remain available for recovered drafts. In-app review fields survive ordinary activity recreation; the durable draft contains measurements, not unfinished review text.

Distance is a filtered estimate: fixes must be no older than 15 seconds, have accuracy at most 40 metres, be newer than the previous fix and not predate this recording segment. Movement at most three metres is ignored. Gaps over 30 seconds and jumps over six metres per second do not add connecting distance. Loss of a location provider pauses the service. No reliable movement means zero distance, never an invented estimate. Journal minutes round up to at least one, and notes preserve the active seconds plus the distance limitation. Zero-distance entries remain editable.

Foreground route-progress location and optional speech remain separate and stop when the activity leaves the foreground. They do not control recording. A physical Fire smoke test of screen-off timing, notification pause, resume, force-stop recovery and journal persistence passed on 24 October 2026 (below). GPS distance, extended battery restrictions and other OS/device combinations remain unverified. This is a private journal tool, not emergency monitoring.

## Remaining device acceptance checklist

1. On API 26+ Fire hardware with a usable location provider, start while visible, lock the screen, walk a measured route, then reopen and check elapsed time/distance. Repeat with notification permission denied on API 33+ and approximate location on API 31+.
2. Pause from the notification, travel, resume in My walks, finish and save. Confirm paused travel/time is excluded, exactly one journal entry exists, notes contain active seconds, and editing/rating/photos still work.
3. Rotate/recreate the activity, swipe away its task, revoke permission, disable location, use Android Stop and force-stop, and reboot. Confirm interruption recovery never silently resumes, and saving/discarding the paused draft works.
4. Test weak fixes, long GPS outages, no GPS hardware, denied location permission, low storage and Fire battery restrictions. Confirm the UI reports unavailable/incomplete distance rather than fabricating it.

Android implementation references: [location permission and foreground services](https://developer.android.com/develop/sensors-and-location/location/permissions), [foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [user stopping a foreground-service app](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping).

## Physical Fire recording smoke test — 24 October 2026

Tested the Amazon-installed `com.trailnest.app` 0.8.1-lat/code 9 on KFTRWI, API 28. Recording service, store, domain engine, panel and save implementation are unchanged between that binary's source and current 0.8.3. This does not claim the newer LAT binary was installed.

- Started with an empty journal and no existing draft. Home/minimised, then screen asleep from about 09:33:45 to 09:34:55 BST. Android reported the recording service foreground with notification ID 41 while asleep. On returning and pausing, active time was 1:47, including the screen-off interval.
- Resumed, expanded the ongoing notification and used **Pause recording**. The app displayed paused at 3:01; a later capture still showed 3:01, excluding the pause interval. Resumed again while visible, then minimised.
- Force-stopped only TrailNest at 09:38:04 BST and verified its process stopped. Reopened My walks: the draft was paused at 3:21 with the interruption/checkpoint-loss warning. It did not silently resume or add process downtime.
- Finished the recovered draft and saved `QA-Recording-Recovery-24Oct`. Exactly one entry appeared, with 201 active seconds in notes, rounded journal duration 4 minutes, and zero distance. Force-stopped/reopened again: one entry remained and the draft was gone. Removed only this synthetic QA entry through the confirmation dialog; journal returned to zero walks.
- The device received no reliable <=40 m fix. Zero distance and the waiting-for-location message were honest. No measured outdoor walk, paused-distance exclusion with real fixes, reboot, permission revocation, API 31/33 behavior or prolonged battery/Doze acceptance is claimed.
- On the isolated 0.8.3 `.draft` package, existing device tests `RecordingUiDeviceTest` and `PersonalDataDeviceTest#savingRecoveredRecordingIsIdempotentAndPreservesJournalEdits` passed (2 tests, 7.168 seconds). The latter exercises duplicate insertion after journal edits in Room; the manual test did not inject a crash between database commit and draft removal.

Sanitized record: `artifacts/recording-fire-2026-10-24.json`. Local screenshots under ignored `artifacts/reports/lat-device/recording-*.png`; no coordinates, receipts or account identifiers are committed. No recording implementation defect was established in this smoke test.
