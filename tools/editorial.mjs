import fs from 'node:fs/promises';import {fileURLToPath} from 'node:url';
const file=new URL('../content/routes.json',import.meta.url);const content=JSON.parse(await fs.readFile(file,'utf8'));
const [action,id,...args]=process.argv.slice(2);const option=name=>args.find(x=>x.startsWith(name+'='))?.slice(name.length+1);
if(action==='validate'){
  for(const r of content.routes){
    if(!r.id||!r.country||!r.source||!r.attribution||!r.licence||!Array.isArray(r.geometry)||r.geometry.length<2)throw Error(`Incomplete provenance/geometry: ${r.id}`);
    if(r.geometry.some(p=>!Number.isFinite(p.lat)||!Number.isFinite(p.lon)||Math.abs(p.lat)>90||Math.abs(p.lon)>180))throw Error(`Invalid geometry: ${r.id}`);
    if(r.publicationStatus==='published'&&(!r.reviewer||!r.reviewedAt||r.verificationStatus!=='editorially_verified'||r.humanApproved!==true))throw Error(`Publication review missing: ${r.id}`);
  }
  console.log(`Validated ${content.routes.length} route records. ${content.routes.filter(r=>r.publicationStatus==='published').length} published.`);
}else if(action==='retire'){
  const r=content.routes.find(r=>r.id===id);if(!r)throw Error('Route not found.');
  const reviewer=option('--reviewer'),reason=option('--reason');if(!reviewer||!reason)throw Error('Provide --reviewer= and --reason=.');
  r.publicationStatus='retired';r.revisionHistory??=[];r.revisionHistory.push({action:'retired',reviewer,reason,at:new Date().toISOString()});
  const temporary=fileURLToPath(file)+'.tmp';await fs.writeFile(temporary,JSON.stringify(content,null,2)+'\n');await fs.rename(temporary,file);console.log('Route retired.');
}else{console.log('Usage: node tools/editorial.mjs validate | retire <id> --reviewer=name --reason=reason');process.exitCode=1;}
