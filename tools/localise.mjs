import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {extractResources,placeholders as placeholder,pluralErrors,sourceHash,escapeAndroid,androidQualifier,hardcodedUiStrings,entryReviewed,sourceIsCurrent} from './localisation-resources.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const load=async p=>JSON.parse(await fs.readFile(path.join(root,p),'utf8'));
const registry=await load('config/locales.json'),glossary=await load('config/glossary.json');
const values=path.join(root,'app/src/main/res/values');
const documents=await Promise.all((await fs.readdir(values)).filter(f=>f.endsWith('.xml')).sort().map(f=>fs.readFile(path.join(values,f),'utf8')));
const {strings:source,plurals}=extractResources(documents);
const flag=process.argv.find(x=>x.startsWith('--translate='));
await fs.mkdir(path.join(root,'localisation'),{recursive:true});
await fs.writeFile(path.join(root,'localisation/source.json'),JSON.stringify(source,null,2)+'\n');
await fs.writeFile(path.join(root,'localisation/plurals-source.json'),JSON.stringify(plurals,null,2)+'\n');
const report={generatedAt:new Date().toISOString(),registryCheckedOn:registry.checkedOn,resourceStrings:Object.keys(source).length,resourcePlurals:Object.keys(plurals).length,fullyLocalised:false,draftOnly:true,resourceSourceSet:'main',locales:[]};
const generated=[];
for(const locale of registry.locales){
  const file=`localisation/${locale.tag}.json`;let translation={};try{translation=await load(file);}catch{}
  if(flag===`--translate=${locale.tag}`){
    const endpoint=process.env.TRAILNEST_TRANSLATION_URL,token=process.env.TRAILNEST_TRANSLATION_TOKEN;
    if(!endpoint||!token)throw Error('Set TRAILNEST_TRANSLATION_URL and TRAILNEST_TRANSLATION_TOKEN in the development environment only.');
    const url=new URL(endpoint);if(url.protocol!=='https:')throw Error('Translation endpoint must use HTTPS.');
    await fs.mkdir(path.join(root,'tools/.cache'),{recursive:true});
    for(const [key,value]of Object.entries(source)){
      if(sourceIsCurrent(value,translation[key])||glossary.safetyCriticalKeys.includes(key)||/(safety|warning|emergency|direction|livestock|closure|accessibility)/i.test(key))continue;
      const cacheKey=crypto.createHash('sha256').update(JSON.stringify([endpoint,locale.tag,key,value,glossary])).digest('hex');
      const cacheFile=path.join(root,'tools/.cache',cacheKey+'.json');let result;
      try{result=JSON.parse(await fs.readFile(cacheFile,'utf8'));}catch{
        const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':`Bearer ${token}`},body:JSON.stringify({source:'en-GB',target:locale.tag,key,text:value,protectedTerms:glossary.protectedTerms,terminology:glossary.terminology[locale.tag]||[]}),signal:AbortSignal.timeout(30000)});
        if(!response.ok)throw Error(`Translation failed with HTTP ${response.status}.`);
        result=await response.json();if(typeof result.translatedText!=='string')throw Error('Endpoint must return {translatedText: string}.');
        await fs.writeFile(cacheFile,JSON.stringify(result));
      }
      translation[key]={text:result.translatedText,sourceHash:sourceHash(value),reviewed:false,provenance:'configured_translation_endpoint',requiresSafetyReview:translation[key]?.requiresSafetyReview===true||/(safety|warning|emergency|access|return|recording|off_route|privacy)/i.test(key+' '+value)};
    }
    await fs.writeFile(path.join(root,file),JSON.stringify(translation,null,2)+'\n');
  }
  const missing=[],invalid=[],needsReview=[];
  for(const[key,value]of Object.entries(source)){
    const entry=translation[key];if(!entry){missing.push(key);continue;}
    if(typeof entry.text!=='string'||!entry.text.trim()||placeholder(value)!==placeholder(entry.text))invalid.push(`${key}: placeholders or empty text`);
    if(!sourceIsCurrent(value,entry))invalid.push(`${key}: stale source`);
    for(const term of glossary.protectedTerms)if(value.includes(term)&&!entry.text?.includes(term))invalid.push(`${key}: protected term ${term}`);
    if(!entryReviewed(entry))needsReview.push(key);
  }
  for(const[key,forms]of Object.entries(plurals)){
    const entry=translation[key];if(!entry){missing.push(key);continue;}
    invalid.push(...pluralErrors(forms,entry,locale.tag).map(error=>`${key}: ${error}`));
    if(!sourceIsCurrent(forms,entry))invalid.push(`${key}: stale source`);
    for(const term of glossary.protectedTerms)if(forms.other.includes(term)&&Object.values(entry.forms||{}).some(text=>!text.includes(term)))invalid.push(`${key}: protected term ${term}`);
    if(!entryReviewed(entry))needsReview.push(key);
  }
  report.locales.push({tag:locale.tag,amazonListing:locale.amazonListing,missing,invalid,needsReview,approved:!missing.length&&!invalid.length&&!needsReview.length&&locale.reviewed===true});
  // --preview remains an alias for existing review workflows. All variants now
  // consume the same generated bundles without changing their review status.
  if(process.argv.includes('--generate')||process.argv.includes('--preview')){
    if(missing.length||invalid.length)throw Error(`${locale.tag}: generation requires complete, structurally valid, current-source translations.`);
    const folder=path.join(root,'app/src/main/res',androidQualifier(locale.tag));
    let xml='<?xml version="1.0" encoding="utf-8"?>\n<!-- UNREVIEWED DRAFT. Active in app; never a release approval. -->\n<resources>\n';
    for(const key of Object.keys(source))xml+=`    <string name="${key}">${escapeAndroid(translation[key].text)}</string>\n`;
    for(const key of Object.keys(plurals)){xml+=`    <plurals name="${key}">\n`;for(const [category,text]of Object.entries(translation[key].forms))xml+=`        <item quantity="${category}">${escapeAndroid(text)}</item>\n`;xml+='    </plurals>\n';}
    generated.push({folder,xml:xml+'</resources>\n'});
  }
}
for(const {folder,xml}of generated){await fs.mkdir(folder,{recursive:true});await fs.writeFile(path.join(folder,'strings.xml'),xml);}
if(generated.length){
  const xml='<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n'+registry.locales.map(locale=>`    <locale android:name="${locale.tag}" />`).join('\n')+'\n</locale-config>\n';
  await fs.writeFile(path.join(root,'app/src/main/res/xml/locales_config.xml'),xml);
}
async function walk(dir){const files=[];for(const ent of await fs.readdir(dir,{withFileTypes:true})){const full=path.join(dir,ent.name);if(ent.isDirectory())files.push(...await walk(full));else if(ent.name.endsWith('.kt'))files.push(full);}return files;}
report.hardcodedComposeStrings=[];
for(const file of await walk(path.join(root,'app/src/main/java'))){const content=await fs.readFile(file,'utf8');const findings=hardcodedUiStrings(content);if(findings.length)report.hardcodedComposeStrings.push({file:path.relative(root,file),count:findings.length,findings});}
report.plurals={status:report.locales.filter(l=>l.amazonListing).every(l=>l.approved)?'passed':'requires_reviewed_translations',resources:Object.keys(plurals),message:'Source extraction and locale plural-category/placeholder validation implemented. Reviewed translations remain required.'};
report.fullyLocalised=report.locales.every(l=>l.approved)&&!report.hardcodedComposeStrings.length;
await fs.writeFile(path.join(root,'artifacts/localisation-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(`Extracted ${Object.keys(source).length} strings and ${Object.keys(plurals).length} plural resources. Audited ${report.locales.length} locale targets. ${report.locales.filter(l=>l.approved).length} approved. Human review remains required. Report: artifacts/localisation-report.json`);
if(process.argv.includes('--release')&&(report.locales.filter(l=>l.amazonListing).some(l=>!l.approved)||report.hardcodedComposeStrings.length||report.plurals.status!=='passed'))process.exitCode=1;
if(process.argv.includes('--check')&&(report.locales.some(l=>l.missing.length||l.invalid.length)||report.hardcodedComposeStrings.length))process.exitCode=1;
