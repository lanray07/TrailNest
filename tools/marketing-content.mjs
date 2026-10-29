import crypto from 'node:crypto';

/** Ignore review timestamps, but invalidate captures when UI wording, plurals,
 * localized example data, headlines or app version change. */
export function captureFingerprint(entries, extra, version) {
  const resources = Object.fromEntries(Object.entries(entries).map(([key, entry]) => [key, entry.text ?? entry.forms]));
  const canonical = value => value && typeof value === 'object' && !Array.isArray(value) ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
  return crypto.createHash('sha256').update(JSON.stringify(canonical({ version, resources, headlines: extra.headlines, sampleWalk: extra.sampleWalk, demoLabel: extra.demoLabel }))).digest('hex');
}
