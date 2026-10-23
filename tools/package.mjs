import fs from 'node:fs/promises';import crypto from 'node:crypto';
const root=new URL('../',import.meta.url);const records=[];
for(const [source,name,signed] of [['app/build/outputs/apk/debug/app-debug.apk','TrailNest-0.1.0-debug.apk',true],['app/build/outputs/apk/release/app-release-unsigned.apk','TrailNest-0.1.0-release-unsigned.apk',false],['app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','TrailNest-0.1.0-device-tests.apk',true]]){
  const target=new URL('artifacts/'+name,root);await fs.copyFile(new URL(source,root),target);const data=await fs.readFile(target);records.push({file:name,bytes:data.length,sha256:crypto.createHash('sha256').update(data).digest('hex'),signed,productionApproved:false});
}
await fs.writeFile(new URL('artifacts/checksums.json',root),JSON.stringify(records,null,2)+'\n');console.log('Packaged development APKs and SHA-256 manifest. Release APK remains unsigned.');
