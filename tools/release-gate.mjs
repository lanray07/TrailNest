import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { auditMarketing } from './marketing-audit.mjs';
const base=new URL('../',import.meta.url);const json=async p=>JSON.parse(await fs.readFile(new URL(p,base),'utf8'));
// Recompute from current resources/policy; an old accepted report is not evidence
// that translations still match the source being considered for release.
const translationCheck=spawnSync(process.execPath,[fileURLToPath(new URL('localise.mjs',import.meta.url)),'--check','--release'],{encoding:'utf8'});
const failures=[];const countries=await json('config/countries.json'),catalogue=await json('content/routes.json'),locales=await json('artifacts/localisation-report.json');
if(translationCheck.status!==0)failures.push('Fresh translation validation failed; run node tools/localise.mjs --check --release.');
if(!countries.countries.some(c=>c.coverage==='supported_verified'&&c.mapProviders.length))failures.push('No verified country coverage or contracted map provider.');
if(!catalogue.routes.some(r=>r.publicationStatus==='published'&&r.humanApproved&&r.reviewer&&r.licence))failures.push('No licensed, editorially approved routes published.');
if(locales.translationReady!==true||locales.locales.some(l=>l.amazonListing&&!l.accepted))failures.push('Locale translations do not pass the configured translation policy.');
if(locales.hardcodedComposeStrings.length)failures.push('Hardcoded app text migration incomplete.');
let evidence={};try{evidence=await json('artifacts/release-evidence.json');}catch{}
for(const [key,label]of Object.entries({fireDeviceTest:'Complete target-device acceptance (partial Kindle evidence exists)',amazonIapVerified:'Production billing configuration and acceptance (LAT checks are separate)',signingConfigured:'Production release signing',operatorAndPrivacyApproved:'Complete operator/privacy/retention review (public support contact exists)',marketingScreenshotsApproved:'Approval of eight honest, localised Android marketing screenshots',accessibilityReviewed:'Human accessibility and spoken-screen-reader review',titleAvailabilityChecked:'Appstore title availability'}))if(evidence[key]!==true)failures.push(`${label} missing.`);
const gradle=await fs.readFile(new URL('app/build.gradle.kts',base),'utf8');
const version=gradle.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)?.[1];
if(!version)failures.push('Android app version unavailable.');
const registry=await json('config/locales.json');
for(const locale of registry.requiredAppLocales||[])if(!registry.locales.some(item=>item.tag===locale))failures.push(`Required initial app locale ${locale} is not implemented.`);
const marketing=await auditMarketing(fileURLToPath(base),registry,version);
failures.push(...marketing.failures);
await fs.writeFile(new URL('artifacts/marketing-audit.json',base),JSON.stringify(marketing,null,2)+'\n');
await fs.writeFile(new URL('artifacts/release-gate.json',base),JSON.stringify({version,releaseReady:failures.length===0,failures},null,2)+'\n');
console.log(failures.length?`Release blocked:\n${failures.map(f=>' - '+f).join('\n')}`:'Release evidence complete.');
process.exitCode=failures.length?1:0;
