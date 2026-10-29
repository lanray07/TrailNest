import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { captureFingerprint } from './marketing-content.mjs';
const root = new URL('../', import.meta.url);
const read = async file => JSON.parse(await fs.readFile(new URL(file, root), 'utf8'));
const registry = await read('config/locales.json');
const copy = await read('marketing/localized-copy.json');
const source = await read('localisation/source.json');
const version = (await fs.readFile(new URL('app/build.gradle.kts', root), 'utf8')).match(/versionName\s*=\s*"([\d.]+)"/)[1];
const headlineKeys = { journal: 'journal_title', recording: 'recording_channel', checklist: 'checklist_title', photos: 'add_photo', gpx: 'ui_import_your_own_gpx', progress: 'ui_your_route_at_a_glance', sharing: 'share_plan_title', settings: 'ui_make_yourself_at_home' };
const content = {};
for (const locale of registry.locales) {
  const entries = await read(`localisation/${locale.tag}.json`);
  const text = key => {
    if (!entries[key]?.text) throw Error(`${locale.tag}: missing marketing resource ${key}`);
    const expected = crypto.createHash('sha256').update(JSON.stringify(source[key])).digest('hex');
    if (entries[key].sourceHash !== expected) throw Error(`${locale.tag}: stale marketing resource ${key}`);
    return entries[key].text;
  };
  const extra = copy[locale.tag];
  if (!extra) throw Error(`Missing metadata for ${locale.tag}`);
  for (const field of ['shortDescription', 'voiceDisclosure', 'sampleWalk', 'demoLabel', 'releaseNotes']) if (typeof extra[field] !== 'string' || !extra[field].trim()) throw Error(`${locale.tag}: missing ${field}`);
  for (const screen of Object.keys(headlineKeys)) if (typeof extra.headlines?.[screen] !== 'string' || !extra.headlines[screen].trim()) throw Error(`${locale.tag}: missing headline ${screen}`);
  const listing = {
    locale: locale.tag, version, status: 'ready_for_submission',
    assetStatus: 'prepared_and_agent_checked; whole_app_release_not_approved',
    amazonStorefrontMapped: locale.amazonListing,
    titleAvailability: 'not_verified', currentBuildTitle: `TrailNest – ${text('ui_walk_journal')}`,
    shortDescription: extra.shortDescription,
    longDescription: [extra.shortDescription,
      `${text('journal_description')} ${text('photo_privacy')}`,
      text('checklist_description'),
      `${text('packs_description')} ${text('ui_imported_routes_remain_private_and_unreviewed_check_access_and_co')}`,
      `${text('recording_description')} ${text('recording_privacy')}`,
      extra.voiceDisclosure,
      text('ui_system_speech_services_may_process_voice_data_under_their_own_pri'),
      `${text('ui_purchasing_is_not_available_in_this_build_no_products_prices_or_p')} ${text('ui_your_checklist_journal_and_one_offline_geometry_pack_are_availabl')}`
    ].join('\n\n'),
    features: [text('ui_walk_journal'), text('checklist_description'), text('add_photo'), text('ui_import_your_own_gpx'), text('language_title')],
    keywords: [text('ui_walk_journal'), text('checklist'), 'GPX', text('recording_channel')],
    releaseNotes: extra.releaseNotes,
    metadataSource: 'https://developer.amazon.com/docs/app-submission/appstore-details.html',
    checkedOn: '2026-10-29', provenance: extra.provenance,
    humanReviewed: false, productionSubmissionApproved: false
  };
  await fs.writeFile(new URL(`marketing/listing-${locale.tag}.json`, root), JSON.stringify(listing, null, 2) + '\n');
  content[locale.tag] = { sampleWalk: extra.sampleWalk, demoLabel: extra.demoLabel,
    contentFingerprint: captureFingerprint(entries, extra, version),
    expectedStrings: Object.fromEntries(Object.entries(entries).filter(([, entry]) => typeof entry.text === 'string').map(([key, entry]) => [key, entry.text])),
    headlines: Object.fromEntries(Object.keys(headlineKeys).map(id => [id, extra.headlines[id]])) };
}
await fs.mkdir(new URL('app/src/androidTest/assets/marketing/', root), { recursive: true });
await fs.writeFile(new URL('app/src/androidTest/assets/marketing/locales.json', root), JSON.stringify(content, null, 2) + '\n');
console.log(`Prepared ${registry.locales.length} localized listings and native-capture content for ${version}. No console upload performed.`);
