# Android 0.2 personal features

This update completes a group of features that need no external mapping or route-content licence. It does not activate a route catalogue, tiles, billing or validated navigation instructions.

## Journal and activity

Create and edit a completed walk’s title, date, distance, duration and notes. Entries retain their identity, personal rating, favourite state and photos when edited. Notes-only edits preserve the exact stored distance rather than converting a rounded display value back into metres. Invalid dates, future completed-walk dates, non-finite distances and invalid durations are rejected.

The journal includes a favourites filter, yearly totals with year selection, and twelve monthly activity rows grouped in the device’s current time zone. Entries render in a lazy list, so off-screen photo thumbnails are not all decoded at once. Dates entered in the editor use the explicitly labelled ISO format; displayed dates and month names use the device locale. Broader reviewed localisation remains unfinished.

## Private photos

Use Add private photo on a saved walk to choose an image through Android’s system document picker. No broad media-library permission or cloud upload is added. Up to eight photos are accepted per walk, with a 20 MB source limit. Images are sampled during decoding, corrected for EXIF orientation and re-encoded as JPEG in the app’s private files directory; source metadata, including GPS EXIF, is not copied. The user’s original remains untouched.

Removing a photo deletes only the private copy. Deleting a walk removes its photo records through a foreign key and cleans its private files. Startup cleanup removes orphan private copies left by interrupted imports. File names are generated UUIDs and are validated before use. No backups or sync are enabled. Photo rendering and metadata removal still require actual-device verification.

Room schema 1→2 adds the photo table and index without changing existing walk/route tables. There is no destructive-migration fallback. A shared database instance and serialized photo operations coordinate file/database changes.

## Checklist

Add, rename, reorder and remove checklist items. Reset clears checks while retaining item names and order. Restoring the default eleven items requires confirmation and discards customization. Removal also requires confirmation. Default v0.1 keys (`kit_0`…`kit_10`) retain their checked state until the first checklist edit writes the new JSON representation. Mutations run inside a single DataStore edit, avoiding a reset made from multiple asynchronous toggles. A maximum of 100 items and 120 characters per title bounds the data.

## Shared plans

Share walk plan now collects an estimated duration, future return time and optional contact details before opening the Sharesheet. The initial duration uses an editable 4 km/h estimate, not reviewed route timing. Shared text includes the start coordinate, distance, explicit return offset and zone ID, personal duration estimate and unreviewed-access status. Return times in daylight-saving gaps or overlaps are rejected rather than silently moved to another time. GPX geometry remains available through the separate export action. No location is sent automatically and no monitoring is provided.

## Resources and checks

New UI labels use Android resources, with plural resources for walk counts and checklist progress. `tools/localise.mjs` now scans every default values XML file and checks translated plural categories and format placeholders. It still blocks locale release until actual translations and human review exist. Error text and other legacy UI still need full resource migration.

The executed SQL regression extracts the actual migration statements from Kotlin and checks existing data and foreign-key behaviour in SQLite. This complements the Android instrumentation test that opens the upgraded database through Room. Device tests also cover checklist editing, journal editing, and EXIF stripping on a synthetic photo. They are compiled when no device is available; compilation is not a passed device run. See `VERIFICATION.md` for the actual results.
