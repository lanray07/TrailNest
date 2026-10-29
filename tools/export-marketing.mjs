import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { captureFingerprint } from './marketing-content.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const read = async file => JSON.parse(await fs.readFile(file, 'utf8'));
const registry = await read(path.join(root, 'config/locales.json'));
const copy = await read(path.join(root, 'marketing/localized-copy.json'));
const manifest = await read(path.join(root, 'marketing/screenshots.json'));
const version = (await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8')).match(/versionName\s*=\s*"([\d.]+)"/)[1];
const sourceDirectories = process.argv.slice(2);
if (!sourceDirectories.length) throw Error('Supply one or more pulled native marketing-captures directories, oldest first.');
const scenes = manifest.requirements.map(item => item.id);
const selected = new Map();
for (const source of sourceDirectories) {
  const directory = path.resolve(root, source);
  const report = await read(path.join(directory, 'captures.json'));
  if (report.package !== 'com.trailnest.app.draft' || report.failures?.length !== 0) throw Error(`Native capture report is not a passing isolated preview run: ${source}`);
  for (const capture of report.captures) {
    if (!registry.locales.some(item => item.tag === capture.locale) || !scenes.includes(capture.screenId)) throw Error('Unexpected capture locale or screen');
    if (capture.file !== `${capture.locale}/${capture.screenId}.png` || capture.version !== version || capture.uiLocale !== capture.locale || capture.overflowCount !== 0) throw Error('Capture path, version, UI locale or layout check mismatch');
    if (capture.headline !== copy[capture.locale].headlines[capture.screenId]) throw Error('Capture headline is stale');
    const entries = await read(path.join(root, `localisation/${capture.locale}.json`));
    if (capture.contentFingerprint !== captureFingerprint(entries, copy[capture.locale], version) || capture.resourceStringsVerified !== Object.values(entries).filter(entry => typeof entry.text === 'string').length) throw Error('Capture resources are stale or were not verified on device');
    selected.set(`${capture.locale}/${capture.screenId}`, { capture, directory });
  }
}
const captures = [];
for (const locale of registry.locales) for (const screen of scenes) {
  const entry = selected.get(`${locale.tag}/${screen}`);
  if (!entry) throw Error(`Missing ${locale.tag}/${screen}`);
  const { capture, directory } = entry;
  const bytes = await fs.readFile(path.join(directory, capture.file));
  const sha256 = crypto.createHash('sha256').update(bytes).digest('hex');
  if (sha256 !== capture.sha256 || bytes.readUInt32BE(16) !== capture.width || bytes.readUInt32BE(20) !== capture.height) throw Error(`Native file changed: ${capture.file}`);
  const file = `marketing/screenshots/${capture.file}`;
  await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true });
  await fs.writeFile(path.join(root, file), bytes);
  captures.push({ ...capture, file, sha256, status: 'captured_pending_visual_review',
    sourcePackage: 'com.trailnest.app.draft', uiSource: 'production_composables_with_localized_demo_data',
    photoSource: screen === 'photos' ? 'Original imaginary landscape from app assets, imported through PhotoStore as an illustrative private photo' : undefined,
    humanReviewed: false, productionSubmissionApproved: false });
}
manifest.status = 'native_exports_pending_visual_review';
manifest.version = version;
manifest.note = 'Eight native PNGs per app locale. Localized demonstration data and original illustrative photo; no real GPS, purchase, licensed-map or public-route claim. Assets prepared locally, not uploaded to Amazon.';
manifest.captures = captures;
await fs.writeFile(path.join(root, 'marketing/screenshots.json'), JSON.stringify(manifest, null, 2) + '\n');
let index = `# TrailNest ${version} localized store assets\n\nNative tablet PNG captures of the real production UI components in the isolated preview app, with localized example data. The photo is the app’s original imaginary landscape used to demonstrate private photo handling. No real purchase, recorded trip, licensed basemap or reviewed public route is implied. No Amazon upload or whole-app release approval is claimed.\n\nAll ${registry.locales.length} app locales have listing copy and eight screenshots. Only locales marked amazonStorefrontMapped in their listing are mapped to the ${registry.locales.filter(item => item.amazonListing).length} storefront localizations in the current registry; other copies are supplemental app-language assets. Human translation approval is not claimed under the existing owner waiver.\n\n| Locale | Copy | Native screenshots |\n| --- | --- | --- |\n`;
for (const locale of registry.locales) index += `| ${locale.tag} | [Listing](listing-${locale.tag}.json) | ${scenes.map(screen => `[${screen}](screenshots/${locale.tag}/${screen}.png)`).join(' · ')} |\n`;
await fs.writeFile(path.join(root, 'marketing/README.md'), index);
console.log(`Exported ${captures.length} unchanged native PNGs for ${registry.locales.length} locales; visual review still required.`);
