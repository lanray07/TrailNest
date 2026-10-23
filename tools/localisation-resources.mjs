import crypto from 'node:crypto';
export const placeholders=text=>[...text.replaceAll('%%','').matchAll(/%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdf]|\{[\w]+\}/g)].map(m=>m[0]).sort().join('|');
export const sourceHash=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
export const xmlText=text=>text.replace(/&(?:amp|lt|gt|quot|apos);|&#(?:x[0-9a-f]+|\d+);/gi,entity=>({'&amp;':'&','&lt;':'<','&gt;':'>','&quot;':'"','&apos;':"'"}[entity]??String.fromCodePoint(entity[2]==='x'?parseInt(entity.slice(3,-1),16):parseInt(entity.slice(2,-1),10)))).replace(/\\([ntr'"\\])/g,(_,c)=>({n:'\n',t:'\t',r:'\r'}[c]??c));
export const escapeAndroid=text=>text.replaceAll('\\','\\\\').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll("'","\\'").replaceAll('"','\\"').replaceAll('\n','\\n').replaceAll('\t','\\t').replaceAll('\r','\\r');
export const androidQualifier=tag=>'values-b+'+tag.replaceAll('-','+');
export function entryReviewed(entry){return entry?.reviewed===true&&typeof entry.reviewer==='string'&&!!entry.reviewer.trim()&&typeof entry.reviewedAt==='string'&&Number.isFinite(Date.parse(entry.reviewedAt))&&(!entry.requiresSafetyReview||(typeof entry.safetyReviewer==='string'&&!!entry.safetyReviewer.trim()));}
export function localeAccepted(audit,locale,policy){
  return audit.missing.length===0&&audit.invalid.length===0&&
    (policy?.humanReviewRequired===false||(audit.needsReview.length===0&&locale.reviewed===true));
}
export const sourceIsCurrent=(source,entry)=>entry?.sourceHash===sourceHash(source);
export function hardcodedUiStrings(content){
  const patterns=[/(?:Text|Heading|Page|SectionLabel|EmptyPanel)\s*\(\s*"/g,/(?:Image|Icon)\s*\([^,\n]+,\s*"/g,/\b(?:notice|message\.value)\s*=\s*"/g,/\bText\(\s*if\s*\([^\n]*?\)\s*"/g];
  return patterns.flatMap(pattern=>[...content.matchAll(pattern)].map(m=>({line:content.slice(0,m.index).split('\n').length,excerpt:content.slice(m.index,m.index+90).split('\n')[0]})));
}
export function extractResources(documents){
  const strings={},plurals={};
  for(const xml of documents){
    for(const m of xml.matchAll(/<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g)){
      if(/translatable="false"/.test(m[2]))continue;
      if(m[1] in strings)throw Error(`Duplicate string ${m[1]}`);strings[m[1]]=xmlText(m[3]);
    }
    for(const m of xml.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)){
      if(m[1] in plurals)throw Error(`Duplicate plurals ${m[1]}`);
      plurals[m[1]]=Object.fromEntries([...m[2].matchAll(/<item\s+quantity="([^"]+)"[^>]*>([\s\S]*?)<\/item>/g)].map(i=>[i[1],xmlText(i[2])]));
      if(!plurals[m[1]].other)throw Error(`Plural ${m[1]} requires an other form`);
    }
  }
  return {strings,plurals};
}
export function pluralErrors(source,entry,tag){
  const forms=entry?.forms||{};const errors=[];
  const categories=new Intl.PluralRules(tag).resolvedOptions().pluralCategories;
  for(const category of categories){
    if(typeof forms[category]!=='string'||!forms[category].trim())errors.push(`missing ${category}`);
    else if(placeholders(forms[category])!==placeholders(source.other))errors.push(`${category} placeholders`);
  }
  return errors;
}
