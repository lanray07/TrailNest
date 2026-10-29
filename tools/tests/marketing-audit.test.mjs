import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { deflateSync, crc32 } from 'node:zlib';
import { spawnSync } from 'node:child_process';
import { auditMarketing, listingErrors } from '../marketing-audit.mjs';
import { captureFingerprint } from '../marketing-content.mjs';

const listing = locale => ({ locale, version: '0.8.5', status: 'ready_for_submission', currentBuildTitle: 'TrailNest', shortDescription: 'Private journal', longDescription: 'Prepare and remember your walks.', releaseNotes: 'Journal improvements.', features: ['Journal', 'Checklist', 'GPX geometry'] });
const registry = { locales: [{ tag: 'en-US', amazonListing: true }, { tag: 'ar', amazonListing: false }] };
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
function png(colour) {
  const chunk = (name, bytes) => {
    const data = Buffer.concat([Buffer.from(name), bytes]);
    const output = Buffer.alloc(bytes.length + 12);
    output.writeUInt32BE(bytes.length); data.copy(output, 4); output.writeUInt32BE(crc32(data), output.length - 4);
    return output;
  };
  const header = Buffer.alloc(13); header.writeUInt32BE(800); header.writeUInt32BE(480, 4); header[8] = 8; header[9] = 2;
  const pixels = Buffer.alloc((800 * 3 + 1) * 480, colour);
  for (let row = 0; row < 480; row++) pixels[row * (800 * 3 + 1)] = 0;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(pixels)), chunk('IEND', Buffer.alloc(0))]);
}
async function fixture(run) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'trailnest-marketing-'));
  try {
    await fs.mkdir(path.join(root, 'marketing'));
    await fs.mkdir(path.join(root, 'localisation'));
    await fs.writeFile(path.join(root, 'marketing/listing-en-US.json'), JSON.stringify(listing('en-US')));
    const manifest = { requirements: Array.from({ length: 8 }, (_, id) => ({ id: `screen-${id}` })), captures: [] };
    const copy = {};
    for (const locale of ['en-US', 'ar']) {
      copy[locale] = { headlines: { journal: `Fixture ${locale}` }, sampleWalk: `Fixture ${locale}`, demoLabel: `Example ${locale}` };
      await fs.writeFile(path.join(root, `localisation/${locale}.json`), JSON.stringify({ title: { text: `Fixture ${locale}` } }));
    }
    await fs.writeFile(path.join(root, 'marketing/localized-copy.json'), JSON.stringify(copy));
    for (const locale of ['en-US', 'ar']) for (let id = 0; id < 8; id++) {
      const bytes = png(id + (locale === 'ar' ? 20 : 1));
      const file = `marketing/${locale}-${id}.png`;
      await fs.writeFile(path.join(root, file), bytes);
      manifest.captures.push({ locale, uiLocale: locale, screenId: `screen-${id}`, version: '0.8.5', status: 'reviewed', file, sha256: hash(bytes), contentFingerprint: captureFingerprint({ title: { text: `Fixture ${locale}` } }, copy[locale], '0.8.5'), resourceStringsVerified: 1 });
    }
    const save = () => fs.writeFile(path.join(root, 'marketing/screenshots.json'), JSON.stringify(manifest));
    await save(); await run(root, manifest, save);
  } finally { await fs.rm(root, { recursive: true, force: true }); }
}

test('listing limits count UTF-8 bytes, reject HTML, drafts and missing locale', () => {
  assert.deepEqual(listingErrors(listing('en-US'), 'en-US'), []);
  assert.ok(listingErrors({ ...listing('ja'), shortDescription: '歩'.repeat(667) }, 'ja').some(error => error.includes('2000')));
  assert.ok(listingErrors({ ...listing('en-US'), longDescription: '<b>Walk</b>', features: ['One'], status: 'draft' }, 'en-US').length >= 3);
  assert.ok(listingErrors(listing('en-US'), 'de').includes('listing locale mismatch'));
  assert.ok(listingErrors(listing('en-US'), 'en-US', '0.8.6').includes('stale listing app version'));
});
test('capture fingerprint rejects same-version wording changes but ignores review bookkeeping', () => {
  const entries = { title: { text: 'Journal', reviewed: false }, count: { forms: { other: '%1$d walks' } } };
  const extra = { headlines: { journal: 'Remember a walk' }, sampleWalk: 'Morning walk', demoLabel: 'Example' };
  const initial = captureFingerprint(entries, extra, '0.8.6');
  assert.equal(initial, captureFingerprint({ ...entries, title: { ...entries.title, reviewed: true } }, extra, '0.8.6'));
  assert.notEqual(initial, captureFingerprint({ ...entries, title: { text: 'Diario' } }, extra, '0.8.6'));
  assert.notEqual(initial, captureFingerprint(entries, { ...extra, demoLabel: 'Esempio' }, '0.8.6'));
});
test('complete locale-specific files pass; missing storefront copy blocks even when in-app translations exist', async () => fixture(async root => {
  assert.equal((await auditMarketing(root, registry, '0.8.5')).ready, true);
  await fs.unlink(path.join(root, 'marketing/listing-en-US.json'));
  const result = await auditMarketing(root, registry, '0.8.5');
  assert.equal(result.ready, false); assert.deepEqual(result.listings[0].errors, ['missing listing']);
}));
test('all supported app languages require screenshots, including non-storefront Arabic', async () => fixture(async (root, manifest, save) => {
  manifest.captures = manifest.captures.filter(capture => capture.locale !== 'ar'); await save();
  const result = await auditMarketing(root, registry, '0.8.5');
  assert.equal(result.screenshots.find(row => row.locale === 'ar').ready, false);
}));
test('reviewed exports with matching PNG hashes and versions still fail after UI wording changes', async () => fixture(async root => {
  await fs.writeFile(path.join(root, 'localisation/ar.json'), JSON.stringify({ title: { text: 'Changed wording' } }));
  const result = await auditMarketing(root, registry, '0.8.5');
  assert.ok(result.screenshots.find(row => row.locale === 'ar').errors.some(error => error.includes('stale or unverified installed wording')));
}));
test('translated headline alone, stale version, duplicate scenes and changed files cannot pass', async () => fixture(async (root, manifest, save) => {
  manifest.captures[8].uiLocale = 'en-US'; manifest.captures[9].version = '0.8.0';
  manifest.captures[10].screenId = manifest.captures[11].screenId;
  await fs.appendFile(path.join(root, manifest.captures[12].file), 'changed export'); await save();
  const errors = (await auditMarketing(root, registry, '0.8.5')).screenshots.find(row => row.locale === 'ar').errors.join(' ');
  for (const text of ['localized app UI', 'stale app version', 'distinct planned', 'hash mismatch']) assert.ok(errors.includes(text), text);
}));
test('private paths, bad image headers and unsupported dimensions are rejected', async () => fixture(async (root, manifest, save) => {
  manifest.captures[0].file = 'marketing/../private.png';
  await fs.writeFile(path.join(root, manifest.captures[1].file), 'not an image');
  const bytes = png(99); bytes.writeUInt32BE(999, 16);
  await fs.writeFile(path.join(root, manifest.captures[2].file), bytes);
  manifest.captures[2].sha256 = hash(bytes); await save();
  const errors = (await auditMarketing(root, registry, '0.8.5')).screenshots[0].errors.join(' ');
  for (const text of ['invalid public PNG path', 'invalid PNG header', 'unsupported tablet dimensions']) assert.ok(errors.includes(text), text);
}));
test('release CLI rejects a stale accepted translation report when fresh validation fails', async () => fixture(async root => {
  await fs.mkdir(path.join(root, 'tools')); await fs.mkdir(path.join(root, 'artifacts')); await fs.mkdir(path.join(root, 'config')); await fs.mkdir(path.join(root, 'content')); await fs.mkdir(path.join(root, 'app'));
  for (const filename of ['release-gate.mjs', 'marketing-audit.mjs', 'marketing-content.mjs']) await fs.copyFile(new URL(`../${filename}`, import.meta.url), path.join(root, 'tools', filename));
  await fs.writeFile(path.join(root, 'tools/localise.mjs'), 'process.exitCode = 1;');
  for (const [filename, data] of Object.entries({
    'config/countries.json': { countries: [] }, 'config/locales.json': { ...registry, requiredAppLocales: ['tr'] },
    'content/routes.json': { routes: [] },
    'artifacts/localisation-report.json': { translationReady: true, locales: [], hardcodedComposeStrings: [] }
  })) await fs.writeFile(path.join(root, filename), JSON.stringify(data));
  await fs.writeFile(path.join(root, 'app/build.gradle.kts'), 'versionName = "0.8.5"');
  const result = spawnSync(process.execPath, [path.join(root, 'tools/release-gate.mjs')], { encoding: 'utf8' });
  assert.equal(result.status, 1);
  const report = JSON.parse(await fs.readFile(path.join(root, 'artifacts/release-gate.json'), 'utf8'));
  assert.equal(report.releaseReady, false);
  assert.ok(report.failures.some(error => error.startsWith('Fresh translation validation failed')));
  assert.ok(report.failures.includes('Required initial app locale tr is not implemented.'));
}));
