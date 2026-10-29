import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const tabletSizes = new Set(['800x480', '1024x600', '1280x720', '1280x800', '1920x1080', '1920x1200', '2560x1600']);
const nonempty = value => typeof value === 'string' && value.trim().length > 0;

export function listingErrors(listing, locale) {
  if (!listing) return ['missing listing'];
  const errors = [];
  if (listing.locale !== locale) errors.push('listing locale mismatch');
  if (listing.status !== 'ready_for_submission') errors.push('listing is still a draft');
  for (const key of ['currentBuildTitle', 'shortDescription', 'longDescription', 'releaseNotes']) {
    if (!nonempty(listing[key])) errors.push(`missing ${key}`);
    else if (/<\/?[a-z][^>]*>/i.test(listing[key])) errors.push(`${key} contains HTML`);
  }
  if (Buffer.byteLength(listing.shortDescription || '', 'utf8') > 2000) errors.push('short description exceeds 2000 UTF-8 bytes');
  if ([...(listing.longDescription || '')].length > 4000) errors.push('long description exceeds 4000 characters');
  if (!Array.isArray(listing.features) || listing.features.length < 3 || listing.features.length > 5 || listing.features.some(value => !nonempty(value))) errors.push('require 3–5 nonempty feature bullets');
  else if (listing.features.some(value => /<\/?[a-z][^>]*>/i.test(value))) errors.push('feature bullets contain HTML');
  return errors;
}

// Only files inside marketing/ may be used as public store evidence. Hashes tie
// the manifest to the reviewed export, not merely a filename or approval flag.
async function screenshotErrors(root, capture, version) {
  const errors = [];
  if (capture.version !== version) errors.push('stale app version');
  if (capture.uiLocale !== capture.locale) errors.push('localized app UI not evidenced');
  if (capture.status !== 'reviewed') errors.push('capture not reviewed');
  if (!nonempty(capture.file) || !/^marketing\/[a-zA-Z0-9_./-]+\.png$/.test(capture.file) || capture.file.split('/').includes('..')) return [...errors, 'invalid public PNG path'];
  try {
    const directory = await fs.realpath(path.join(root, 'marketing'));
    const filename = await fs.realpath(path.join(root, capture.file));
    if (!filename.startsWith(directory + path.sep)) return [...errors, 'image is outside marketing directory'];
    const bytes = await fs.readFile(filename);
    if (bytes.length < 33 || !bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) || bytes.toString('ascii', 12, 16) !== 'IHDR') return [...errors, 'invalid PNG header'];
    const width = bytes.readUInt32BE(16), height = bytes.readUInt32BE(20);
    if (!tabletSizes.has(`${Math.max(width, height)}x${Math.min(width, height)}`)) errors.push('unsupported tablet dimensions');
    if (crypto.createHash('sha256').update(bytes).digest('hex') !== capture.sha256) errors.push('image hash mismatch');
  } catch { errors.push('image file unavailable'); }
  return errors;
}

export async function auditMarketing(root, registry, version) {
  const listingLocales = registry.locales.filter(locale => locale.amazonListing).map(locale => locale.tag);
  const screenshotLocales = registry.locales.map(locale => locale.tag);
  const failures = [], listings = [], screenshots = [];
  let manifest = {};
  try { manifest = JSON.parse(await fs.readFile(path.join(root, 'marketing/screenshots.json'), 'utf8')) || {}; } catch {}
  for (const locale of listingLocales) {
    let listing;
    try { listing = JSON.parse(await fs.readFile(path.join(root, `marketing/listing-${locale}.json`), 'utf8')); } catch {}
    const errors = listingErrors(listing, locale);
    listings.push({ locale, ready: errors.length === 0, errors });
    if (errors.length) failures.push(`Appstore listing ${locale}: ${errors.join('; ')}.`);
  }
  const requiredScreens = Array.isArray(manifest.requirements) ? manifest.requirements.map(item => item?.id) : [];
  const validPlan = requiredScreens.length === 8 && new Set(requiredScreens).size === 8 && requiredScreens.every(nonempty);
  if (!validPlan) failures.push('Marketing plan must name eight distinct implemented screens (owner requirement).');
  const captures = Array.isArray(manifest.captures) ? manifest.captures : [];
  for (const locale of screenshotLocales) {
    const selected = captures.filter(capture => capture.locale === locale);
    const errors = [];
    if (selected.length !== 8 || !validPlan || requiredScreens.some(id => selected.filter(capture => capture.screenId === id).length !== 1)) errors.push('eight distinct planned screen captures missing');
    if (new Set(selected.map(capture => capture.sha256)).size !== selected.length) errors.push('duplicate screen images');
    for (const capture of selected) errors.push(...(await screenshotErrors(root, capture, version)).map(error => `${capture.screenId}: ${error}`));
    screenshots.push({ locale, ready: errors.length === 0, errors });
    if (errors.length) failures.push(`Localized screenshots ${locale}: ${errors.join('; ')}.`);
  }
  return { ready: failures.length === 0, version, listings, screenshots, failures };
}
