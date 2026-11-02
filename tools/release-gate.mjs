import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import {createHash} from 'node:crypto';
import { auditMarketing } from './marketing-audit.mjs';
import {publishableRoute} from './editorial-review.mjs';
import {limitedCoverageAccepted} from './release-coverage.mjs';
const base=new URL('../',import.meta.url);const json=async p=>JSON.parse(await fs.readFile(new URL(p,base),'utf8'));
// Recompute from current resources/policy; an old accepted report is not evidence
// that translations still match the source being considered for release.
const translationCheck=spawnSync(process.execPath,[fileURLToPath(new URL('localise.mjs',import.meta.url)),'--check','--release'],{encoding:'utf8'});
const failures=[];const countries=await json('config/countries.json'),catalogue=await json('content/routes.json'),locales=await json('artifacts/localisation-report.json');
if(translationCheck.status!==0)failures.push('Fresh translation validation failed; run node tools/localise.mjs --check --release.');
const editorialPolicy=await json('config/editorial-policy.json');
if(!catalogue.routes.some(r=>publishableRoute(r,editorialPolicy)))failures.push('No licensed, editorially approved routes published.');
if(locales.translationReady!==true||locales.locales.some(l=>l.amazonListing&&!l.accepted))failures.push('Locale translations do not pass the configured translation policy.');
if(locales.hardcodedComposeStrings.length)failures.push('Hardcoded app text migration incomplete.');
let evidence={};try{evidence=await json('artifacts/release-evidence.json');}catch{}
for(const [key,label]of Object.entries({fireDeviceTest:'Scoped target-device acceptance',amazonIapVerified:'Production billing configuration and pre-release verification',signingConfigured:'Production release signing',operatorAndPrivacyApproved:'Operator/privacy/retention review',marketingScreenshotsApproved:'Approval of honest localised Android marketing screenshots',accessibilityReviewed:'Human accessibility and spoken-screen-reader review',titleAvailabilityChecked:'Appstore title availability'}))if(evidence[key]!==true)failures.push(`${label} missing.`);
const gradle=await fs.readFile(new URL('app/build.gradle.kts',base),'utf8');
const version=gradle.match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)?.[1];
const versionCode=Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);
if(!version)failures.push('Android app version unavailable.');
if(!Number.isInteger(versionCode)||versionCode<=0)failures.push('Android version code unavailable.');
let billingQualified=false;
try{
  const b=await json('config/billing/production.json'),a=await json('artifacts/billing-pre-release-acceptance-0.8.7.json');
  const apk=await json('artifacts/production-apk-0.8.7.json'),upload=await json('artifacts/amazon-localized-draft-upload-2026-11-02.json');
  const source=await json('artifacts/billing-source-continuity-0.8.7.json'),cpu=await json('artifacts/cloudflare-signing-cpu-2026-11-02.json');
  const lat=await json('artifacts/amazon-lat-test2-checkout-2026-10-27.json'),expired=await json('artifacts/amazon-lat-test2-expiry-device-2026-10-29.json');
  const fields=apk.productionBindings;
  let sourcesCurrent=Object.keys(a.sourceSha256||{}).length>=5;
  for(const [name,expected]of Object.entries(a.sourceSha256||{})){
    if(!/^(app\/src\/(main\/java\/com\/trailnest\/app\/billing|release\/assets)\/|billing-server\/)/.test(name)||name.includes('..')){sourcesCurrent=false;break;}
    const value=(await fs.readFile(new URL(name,base),'utf8')).replace(/\r\n/g,'\n');
    if(createHash('sha256').update(value).digest('hex')!==expected)sourcesCurrent=false;
  }
  billingQualified=a.qualification==='pre_release'&&a.version===version&&a.versionCode===versionCode&&
    apk.version===version&&apk.versionCode===versionCode&&apk.sha256===upload.apk.sha256&&
    fields.BILLING_ENABLED===true&&fields.DEBUG===false&&fields.LAT_BUILD===false&&fields.BILLING_ENVIRONMENT==='production'&&
    fields.APPLICATION_ID===b.applicationId&&fields.BILLING_PARENT_SKU===b.parentSku&&fields.BILLING_ANNUAL_SKU===b.annualSku&&
    fields.BILLING_VERIFICATION_URL===b.verificationUrl&&apk.publicKeySpkiSha256===b.publicKeySpkiSha256&&
    a.parentSku===b.parentSku&&a.annualSku===b.annualSku&&a.productionCatalogStatus==='Approved'&&
    a.readinessHttp===204&&a.invalidReceiptHttp===403&&a.invalidReceiptLeaseReturned===false&&
    a.workerSignatureCompatibilityPassed===true&&sourcesCurrent&&source.androidBillingEngineUnchanged===true&&source.receiptGuardLogicUnchanged===true&&
    lat.signedPurchaseVerificationCompleted===true&&lat.actualAcceleratedRenewalObserved===true&&lat.actualAcceleratedExpiryObserved===true&&
    expired.onlineRestore.premiumOffAfterRestore===true&&expired.coldOfflineRestart.premiumActive===false&&expired.offlineRestore.premiumActiveAfterRestore===false&&
    cpu.webCryptoSignedResponsesVerified>=16&&cpu.temporaryWorkerRemoved===true&&
    [cpu.webCryptoCold1,cpu.webCryptoCold2].every(r=>r.requests>=8&&r.errors===0&&r.cpuP99Microseconds>0&&r.cpuP99Microseconds<10000);
}catch{}
if(!billingQualified)failures.push('Current APK, source-bound pre-release billing and deployed QA signing/CPU evidence missing or stale.');
let scopedCoverage=false;
try{
  scopedCoverage=limitedCoverageAccepted(countries.countries,await json('config/release-scope.json'),
    await json('artifacts/fire-acceptance-0.8.7.json'),catalogue.routes,editorialPolicy,version,versionCode);
}catch{}
if(!scopedCoverage&&!countries.countries.some(c=>c.coverage==='supported_verified'&&c.mapProviders.length))failures.push('Declared launch coverage lacks matching provider/device acceptance.');
const registry=await json('config/locales.json');
for(const locale of registry.requiredAppLocales||[])if(!registry.locales.some(item=>item.tag===locale))failures.push(`Required initial app locale ${locale} is not implemented.`);
const marketing=await auditMarketing(fileURLToPath(base),registry,version);
failures.push(...marketing.failures);
await fs.writeFile(new URL('artifacts/marketing-audit.json',base),JSON.stringify(marketing,null,2)+'\n');
await fs.writeFile(new URL('artifacts/release-gate.json',base),JSON.stringify({version,versionCode,qualification:'scoped_pre_submission',
  releaseReady:failures.length===0,realProductionPurchaseVerified:false,failures},null,2)+'\n');
console.log(failures.length?`Release blocked:\n${failures.map(f=>' - '+f).join('\n')}`:'Release evidence complete.');
process.exitCode=failures.length?1:0;
