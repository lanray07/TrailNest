import {test} from 'node:test';
import assert from 'node:assert/strict';
import {assembleRelation} from '../osm-route.mjs';
const p=lon=>({lat:51,lon});
const way=(id,values)=>({type:'way',ref:id,role:'',geometry:values.map(p)});
const relation=members=>({type:'relation',id:1,tags:{},members});
test('joins exact endpoints and reverses connected ways without editing source',()=>{
 const source=relation([way(1,[0,1]),way(2,[2,1]),way(3,[2,3])]);
 const result=assembleRelation(source);assert.deepEqual(result.points.map(p=>p.lon),[0,1,2,3]);
 assert.deepEqual(result.wayIds,[1,2,3]);assert.deepEqual(source.members[1].geometry.map(p=>p.lon),[2,1]);
});
test('disconnected, branching, invalid and missing geometry never gain invented connectors',()=>{
 for(const members of [[way(1,[0,1]),way(2,[2,3])],[way(1,[0,1]),way(2,[1,2]),way(3,[1,3])],
   [way(1,[0,200])],[{type:'way',ref:1}], [{type:'relation',ref:2}]])assert.throws(()=>assembleRelation(relation(members)));
});
test('round trip must actually close',()=>{
 const source=relation([way(1,[0,1]),way(2,[1,2])]);source.tags.roundtrip='yes';assert.throws(()=>assembleRelation(source));
 const draft=assembleRelation(source,{allowOpenDraft:true});assert.equal(draft.roundtripGap,true);assert.deepEqual(draft.points.map(p=>p.lon),[0,1,2]);
 source.members.push(way(3,[2,0]));assert.equal(assembleRelation(source).points.length,4);
});
