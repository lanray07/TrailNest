import {test} from 'node:test';
import assert from 'node:assert/strict';
import {limitedCoverageAccepted} from '../release-coverage.mjs';

test('limited launch requires real device/offline evidence and publishable scoped routes',()=>{
  const country=[{iso:'GB',coverage:'supported_limited',offlineMaps:true,mapProviders:['openfreemap']}];
  const scope={version:'0.8.7',versionCode:16,countryWideCoverage:false,mapScope:'saved_initial_route_viewport',
    deviceModels:['KFTRWI'],routeIds:['section'],limitations:'Not field checked; no country-wide claim',contentCountry:'GB',provider:'openfreemap'};
  const evidence={version:'0.8.7',versionCode:16,deviceModel:'KFTRWI',coreDeviceTestsPassed:13,layoutConfigurationsPassed:88,
    coldOfflineMapPassed:true,ambientCacheClearedWhileOffline:true,interruptResumeRegionCount:1,wifiRestored:true};
  const routes=[{id:'section',country:'GB',licence:'ODbL-1.0',source:'https://www.openstreetmap.org',
    attribution:'OSM contributors',reviewer:'reviewer',reviewedAt:'2026-11-01',publicationStatus:'published',
    geometryStatus:'continuous',geometry:[{lat:51,lon:0},{lat:51.001,lon:0}],humanApproved:true,verificationStatus:'editorially_verified'}];
  const accepts=(s=scope,e=evidence,r=routes,c=country)=>limitedCoverageAccepted(c,s,e,r,{},'0.8.7',16);
  assert.equal(accepts(),true);
  for(const patch of [{version:'0.8.6'},{versionCode:15},{countryWideCoverage:true},{routeIds:['unpublished-loop']},
    {deviceModels:['OTHER']},{limitations:''},{mapScope:'worldwide'}])assert.equal(accepts({...scope,...patch}),false);
  for(const patch of [{coldOfflineMapPassed:false},{ambientCacheClearedWhileOffline:false},
    {interruptResumeRegionCount:2},{wifiRestored:false},{coreDeviceTestsPassed:0}])assert.equal(accepts(scope,{...evidence,...patch}),false);
  assert.equal(accepts(scope,evidence,[{...routes[0],publicationStatus:'draft'}]),false);
  assert.equal(accepts(scope,evidence,routes,[{...country[0],coverage:'planned'}]),false);
});
