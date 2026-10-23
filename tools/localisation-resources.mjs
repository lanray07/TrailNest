export const placeholders=text=>[...text.matchAll(/%(?:\d+\$)?[sdf]|\{[\w]+\}/g)].map(m=>m[0]).sort().join('|');
export function extractResources(documents){
  const strings={},plurals={};
  for(const xml of documents){
    for(const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)){
      if(m[1] in strings)throw Error(`Duplicate string ${m[1]}`);strings[m[1]]=m[2];
    }
    for(const m of xml.matchAll(/<plurals\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)){
      if(m[1] in plurals)throw Error(`Duplicate plurals ${m[1]}`);
      plurals[m[1]]=Object.fromEntries([...m[2].matchAll(/<item\s+quantity="([^"]+)"[^>]*>([\s\S]*?)<\/item>/g)].map(i=>[i[1],i[2]]));
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
