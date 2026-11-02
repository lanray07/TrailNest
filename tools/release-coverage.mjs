import {publishableRoute} from './editorial-review.mjs';

// A scoped launch must name its actual routes/device and retain its limitations.
// This does not turn a small tested area into country-wide coverage.
export function limitedCoverageAccepted(countries,scope,evidence,routes,policy,version,versionCode){
  if(!Number.isInteger(versionCode)||versionCode<=0||scope?.versionCode!==versionCode||evidence?.versionCode!==versionCode||
    scope?.version!==version||evidence?.version!==version||scope?.countryWideCoverage!==false||
    scope?.mapScope!=='saved_initial_route_viewport'||scope?.deviceModels?.length!==1||
    scope.deviceModels[0]!==evidence.deviceModel||!scope.routeIds?.length||
    new Set(scope.routeIds).size!==scope.routeIds.length||!scope.limitations?.trim())return false;
  if(evidence.coreDeviceTestsPassed!==13||evidence.layoutConfigurationsPassed!==88||
    evidence.coldOfflineMapPassed!==true||evidence.ambientCacheClearedWhileOffline!==true||
    evidence.interruptResumeRegionCount!==1||evidence.wifiRestored!==true)return false;
  const country=countries.find(c=>c.iso===scope.contentCountry);
  if(country?.coverage!=='supported_limited'||country.offlineMaps!==true||
    !country.mapProviders?.includes(scope.provider))return false;
  return scope.routeIds.every(id=>{
    const route=routes.find(r=>r.id===id);
    return route?.country===scope.contentCountry&&publishableRoute(route,policy);
  });
}
