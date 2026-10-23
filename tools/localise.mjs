import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const load=async p=>JSON.parse(await fs.readFile(path.join(root,p),'utf8'));
const registry=await load('config/locales.json'),glossary=await load('config/glossary.json');
const xml=await fs.readFile(path.join(root,'app/src/main/res/values/strings.xml'),'utf8');
const source=Object.fromEntries([...xml.matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)].map(m=>[m[1],m[2]]));
const placeholder=text=>[...text.matchAll(/%(?:\d+\$)?[sdf]|\{[\w]+\}/g)].map(m=>m[0]).sort().join('|');
const flag=process.argv.find(x=>x.startsWith('--translate='));
await fs.mkdir(path.join(root,'localisation'),{recursive:true});
await fs.writeFile(path.join(root,'localisation/source.json'),JSON.stringify(source,null,2)+'\n');
const report={generatedAt:new Date().toISOString(),registryCheckedOn:registry.checkedOn,resourceStrings:Object.keys(source).length,fullyLocalised:false,locales:[]};
for(const locale of registry.locales){
  const file=`localisation/${locale.tag}.json`;let translation={};try{translation=await load(file);}catch{}
  if(flag===`--translate=${locale.tag}`){
    const endpoint=process.env.TRAILNEST_TRANSLATION_URL,token=process.env.TRAILNEST_TRANSLATION_TOKEN;
    if(!endpoint||!token)throw Error('Set TRAILNEST_TRANSLATION_URL and TRAILNEST_TRANSLATION_TOKEN in the development environment only.');
    const url=new URL(endpoint);if(url.protocol!=='https:')throw Error('Translation endpoint must use HTTPS.');
    await fs.mkdir(path.join(root,'tools/.cache'),{recursive:true});
    for(const [key,value]of Object.entries(source)){
      if(translation[key]||glossary.safetyCriticalKeys.includes(key)||/(warning|emergency|direction|livestock|closure|accessibility)/i.test(key))continue;
      const cacheKey=crypto.createHash('sha256').update(JSON.stringify([endpoint,locale.tag,key,value,glossary])).digest('hex');
      const cacheFile=path.join(root,'tools/.cache',cacheKey+'.json');let result;
      try{result=JSON.parse(await fs.readFile(cacheFile,'utf8'));}catch{
        const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json','Authorization':`Bearer ${token}`},body:JSON.stringify({source:'en-GB',target:locale.tag,key,text:value,protectedTerms:glossary.protectedTerms,terminology:glossary.terminology[locale.tag]||[]}),signal:AbortSignal.timeout(30000)});
        if(!response.ok)throw Error(`Translation failed with HTTP ${response.status}.`);
        result=await response.json();if(typeof result.translatedText!=='string')throw Error('Endpoint must return {translatedText: string}.');
        await fs.writeFile(cacheFile,JSON.stringify(result));
      }
      translation[key]={text:result.translatedText,reviewed:false};
    }
    await fs.writeFile(path.join(root,file),JSON.stringify(translation,null,2)+'\n');
  }
  const missing=[],invalid=[],needsReview=[];
  for(const[key,value]of Object.entries(source)){
    const entry=translation[key];if(!entry){missing.push(key);continue;}
    if(typeof entry.text!=='string'||placeholder(value)!==placeholder(entry.text))invalid.push(`${key}: placeholders`);
    for(const term of glossary.protectedTerms)if(value.includes(term)&&!entry.text?.includes(term))invalid.push(`${key}: protected term ${term}`);
    if(!entry.reviewed||!entry.reviewer||!entry.reviewedAt)needsReview.push(key);
  }
  report.locales.push({tag:locale.tag,amazonListing:locale.amazonListing,missing,invalid,needsReview,approved:!missing.length&&!invalid.length&&!needsReview.length&&locale.reviewed});
}
async function walk(dir){const files=[];for(const ent of await fs.readdir(dir,{withFileTypes:true})){const full=path.join(dir,ent.name);if(ent.isDirectory())files.push(...await walk(full));else if(ent.name.endsWith('.kt'))files.push(full);}return files;}
report.hardcodedComposeStrings=[];
for(const file of await walk(path.join(root,'app/src/main/java'))){const content=await fs.readFile(file,'utf8');const count=[...content.matchAll(/(?:Text|Heading|Page|SectionLabel)\("/g)].length;if(count)report.hardcodedComposeStrings.push({file:path.relative(root,file),count});}
report.plurals={status:'not_implemented',message:'No plurals resources exist yet. Convert quantity-dependent hardcoded UI to Android plurals before locale release.'};
await fs.writeFile(path.join(root,'artifacts/localisation-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(`Extracted ${Object.keys(source).length} resource strings. Audited ${report.locales.length} locale targets. ${report.locales.filter(l=>l.approved).length} approved. Hardcoded Compose strings and plurals still require migration. Report: artifacts/localisation-report.json`);
if(process.argv.includes('--release')&&(report.locales.filter(l=>l.amazonListing).some(l=>!l.approved)||report.hardcodedComposeStrings.length||report.plurals.status!=='passed'))process.exitCode=1;
