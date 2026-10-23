import fs from 'node:fs/promises';
const base=new URL('../',import.meta.url);const json=async p=>JSON.parse(await fs.readFile(new URL(p,base),'utf8'));
const failures=[];const countries=await json('config/countries.json'),catalogue=await json('content/routes.json'),locales=await json('artifacts/localisation-report.json');
if(!countries.countries.some(c=>c.coverage==='supported_verified'&&c.mapProviders.length))failures.push('No verified country coverage or contracted map provider.');
if(!catalogue.routes.some(r=>r.publicationStatus==='published'&&r.humanApproved&&r.reviewer&&r.licence))failures.push('No licensed, editorially approved routes published.');
if(locales.locales.some(l=>l.amazonListing&&!l.approved))failures.push('Listing locale translations and human review incomplete.');
if(locales.hardcodedComposeStrings.length)failures.push('Hardcoded app text migration incomplete.');
let evidence={};try{evidence=await json('artifacts/release-evidence.json');}catch{}
for(const [key,label]of Object.entries({fireDeviceTest:'Physical Fire tablet test evidence',amazonIapVerified:'Amazon SDK, product and server RVS verification',signingConfigured:'Release signing',operatorAndPrivacyApproved:'Operator, support contact and privacy review',marketingScreenshotsApproved:'Eight honest, localised Android marketing screenshots',accessibilityReviewed:'Accessibility and RTL review',titleAvailabilityChecked:'Appstore title availability'}))if(evidence[key]!==true)failures.push(`${label} missing.`);
await fs.writeFile(new URL('artifacts/release-gate.json',base),JSON.stringify({releaseReady:failures.length===0,failures},null,2)+'\n');
console.log(failures.length?`Release blocked:\n${failures.map(f=>' - '+f).join('\n')}`:'Release evidence complete.');
process.exitCode=failures.length?1:0;
