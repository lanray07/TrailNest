import fs from 'node:fs/promises';import crypto from 'node:crypto';
const root=new URL('../',import.meta.url);const records=[];
const version=(await fs.readFile(new URL('app/build.gradle.kts',root),'utf8')).match(/versionName\s*=\s*"(\d+\.\d+\.\d+)"/)?.[1];if(!version)throw Error('Android versionName is missing.');
for(const [source,name,signed] of [['app/build/outputs/apk/debug/app-debug.apk',`TrailNest-${version}-debug.apk`,true],['app/build/outputs/apk/release/app-release-unsigned.apk',`TrailNest-${version}-release-unsigned.apk`,false],['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',`TrailNest-${version}-device-tests.apk`,true],['app/build/outputs/apk/translationPreview/app-translationPreview.apk',`TrailNest-${version}-translation-preview.apk`,true]]){
  const draft=source.includes('/translationPreview/');
  if(!source.includes('/androidTest/')){const metadata=JSON.parse(await fs.readFile(new URL('output-metadata.json',new URL(source,root)),'utf8'));if(metadata.applicationId!==`com.trailnest.app${draft?'.draft':''}`||!metadata.elements?.length||metadata.elements.some(item=>item.versionName!==version+(draft?'-draft':'')))throw Error(`Rebuild ${source}: APK metadata does not match version ${version}.`);}
  const target=new URL('artifacts/'+name,root);await fs.copyFile(new URL(source,root),target);const data=await fs.readFile(target);records.push({file:name,bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex'),signed,productionApproved:false});
}
await fs.writeFile(new URL('artifacts/checksums.json',root),JSON.stringify(records,null,2)+'\n');console.log('Packaged development APKs and SHA-256 manifest. Release APK remains unsigned.');
