import {test} from 'node:test';
import assert from 'node:assert/strict';
import {publishableRoute} from '../editorial-review.mjs';
const sample=()=>({licence:'ODbL-1.0',source:'https://www.openstreetmap.org/relation/1',attribution:'OSM',reviewer:'Codex (AI)',reviewedAt:'2026-11-01',
 geometry:[{lat:51,lon:0},{lat:51,lon:.01}],geometryStatus:'continuous',publicationStatus:'published',humanApproved:false,
 verificationStatus:'ai_desk_reviewed',reviewMethod:'AI desk review; not field checked',accessReview:{result:'accepted_with_limits',limitations:'No field inspection; follow current notices.',sources:[{url:'https://example.org/access',checkedOn:'2026-11-01'},{url:'https://example.org/map',checkedOn:'2026-11-01'}]}});
test('AI review requires explicit owner policy and truthful method and limits',()=>{
 const r=sample(),policy={allowAiDeskReview:true,authorization:'Owner requested AI review'};
 assert(publishableRoute(r,policy));assert(!publishableRoute(r,{}));
 for(const patch of [{humanApproved:true},{reviewMethod:'Human reviewed'},{geometryStatus:'source_roundtrip_gap'},
  {reviewedAt:null},{accessReview:{...r.accessReview,sources:[]}},{geometry:[{lat:200,lon:0},{lat:51,lon:.1}]}])assert(!publishableRoute({...r,...patch},policy));
});
