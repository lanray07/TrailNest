import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';

const coordinate=p=>p&&Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180;
const same=(a,b)=>a.lat===b.lat&&a.lon===b.lon;
/** Connect only exact shared endpoints. Never fabricate a connector between
 * disconnected public ways, or silently choose a branch as a walk itinerary. */
export function assembleRelation(relation,{allowOpenDraft=false}={}){
  if(relation?.type!=='relation'||!Number.isSafeInteger(relation.id)||!Array.isArray(relation.members))throw Error('Invalid relation');
  if(relation.members.some(m=>m.type!=='way'||!['','forward','backward'].includes(m.role??'')))throw Error('Unsupported relation member');
  const remaining=relation.members.map(m=>{
    if(!Array.isArray(m.geometry)||m.geometry.length<2||!m.geometry.every(coordinate))throw Error('Missing or invalid way geometry');
    return {id:m.ref,geometry:m.role==='backward'?[...m.geometry].reverse():[...m.geometry]};
  });
  if(!remaining.length)throw Error('Empty route');
  const degree=new Map();
  for(const way of remaining)for(const p of [way.geometry[0],way.geometry.at(-1)]){
    const key=`${p.lat},${p.lon}`;degree.set(key,(degree.get(key)??0)+1);
  }
  const ends=[...degree].filter(([,n])=>n===1).map(([key])=>key);
  if([...degree.values()].some(n=>n>2)||![0,2].includes(ends.length))throw Error('Ambiguous route graph');
  const firstIndex=ends.length?remaining.findIndex(w=>[w.geometry[0],w.geometry.at(-1)].some(p=>`${p.lat},${p.lon}`===ends[0])):0;
  const [first]=remaining.splice(firstIndex,1);
  if(ends.length&&`${first.geometry[0].lat},${first.geometry[0].lon}`!==ends[0])first.geometry.reverse();
  const points=first.geometry.map(p=>({lat:p.lat,lon:p.lon}));const ids=[first.id];
  while(remaining.length){
    const end=points.at(-1);
    const candidates=remaining.flatMap((way,index)=>same(end,way.geometry[0])?[{index,reverse:false}]:same(end,way.geometry.at(-1))?[{index,reverse:true}]:[]);
    if(candidates.length!==1)throw Error(candidates.length?'Ambiguous route branch':'Disconnected route geometry');
    const {index,reverse}=candidates[0];const [way]=remaining.splice(index,1);if(reverse)way.geometry.reverse();
    points.push(...way.geometry.slice(1).map(p=>({lat:p.lat,lon:p.lon})));ids.push(way.id);
  }
  const roundtripGap=relation.tags?.roundtrip==='yes'&&!same(points[0],points.at(-1));
  if(roundtripGap&&!allowOpenDraft)throw Error('Round trip does not close');
  return {points,wayIds:ids,roundtripGap};
}
export async function importDraft(input,output,{allowOpenDraft=false}={}){
  const bytes=await fs.readFile(input);const source=JSON.parse(bytes);
  if(source.elements?.length!==1)throw Error('Expected exactly one relation');
  const relation=source.elements[0];const {points,wayIds,roundtripGap}=assembleRelation(relation,{allowOpenDraft});
  const catalogue=JSON.parse(await fs.readFile(output,'utf8'));
  const id=`osm-relation-${relation.id}`;
  if(catalogue.routes.some(r=>r.id===id))throw Error('Route already exists; review revisions explicitly');
  catalogue.routes.push({id,title:relation.tags.name,country:'GB',source:`https://www.openstreetmap.org/relation/${relation.id}`,
    licence:'ODbL-1.0',licenceUrl:'https://opendatacommons.org/licenses/odbl/1-0/',attribution:'© OpenStreetMap contributors',
    sourceFile:input.replaceAll('\\','/'),sourceSha256:crypto.createHash('sha256').update(bytes).digest('hex'),sourceTimestamp:source.osm3s?.timestamp_osm_base??null,
    sourceRouteType:relation.tags.route,sourceWayIds:wayIds,geometry:points,geometryStatus:roundtripGap?'source_roundtrip_gap':'continuous',publicationStatus:'draft',verificationStatus:'awaiting_human_review',
    humanApproved:false,reviewer:null,reviewedAt:null,accessReview:null,difficulty:null,
    editorialNotes:'OSM labels this relation as a bicycle route. The Royal Parks identifies the Tamsin Trail for walking and cycling; pedestrian access, current diversions and the exact geometry still require human review. No turn instructions or accessibility suitability are asserted.',
    accessSources:['https://www.royalparks.org.uk/visit/parks/richmond-park','https://www.royalparks.org.uk/visit/parks/richmond-park/faqs'],
    revisionHistory:[{action:'licensed_source_import',at:'2026-11-01',reviewer:'automated import; not human approval'}]});
  await fs.writeFile(output,JSON.stringify(catalogue,null,2)+'\n');
  console.log(`Imported ${id}: ${points.length} points, ${wayIds.length} connected ways; unpublished.`);
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const [input,output]=process.argv.slice(2);if(!input||!output)throw Error('Usage: node tools/osm-route.mjs <source.json> <catalogue.json>');
  await importDraft(input,output,{allowOpenDraft:process.argv.includes('--allow-open-draft')});
}
