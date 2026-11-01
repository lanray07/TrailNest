export function publishableRoute(route,policy){
  const date=Date.parse(route.reviewedAt);
  const provenance=route.licence&&route.source&&route.attribution&&route.reviewer&&Number.isFinite(date);
  const geometry=Array.isArray(route.geometry)&&route.geometry.length>=2&&route.geometry.every(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180);
  if(!provenance||!geometry||route.publicationStatus!=='published'||route.geometryStatus!=='continuous')return false;
  if(route.humanApproved===true&&route.verificationStatus==='editorially_verified')return true;
  return policy?.allowAiDeskReview===true&&typeof policy.authorization==='string'&&!!policy.authorization.trim()&&
    route.humanApproved===false&&route.verificationStatus==='ai_desk_reviewed'&&route.reviewMethod==='AI desk review; not field checked'&&
    route.accessReview?.result==='accepted_with_limits'&&Array.isArray(route.accessReview.sources)&&route.accessReview.sources.length>=2&&
    route.accessReview.sources.every(source=>source.url?.startsWith('https://')&&Number.isFinite(Date.parse(source.checkedOn)))&&
    typeof route.accessReview.limitations==='string'&&!!route.accessReview.limitations.trim();
}
