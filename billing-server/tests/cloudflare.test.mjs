import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {createWorkerHandler,productionVerifier} from '../cloudflare-handler.mjs';
import {VerificationError} from '../verifier.mjs';
const env=()=>({VERIFY_RATE_LIMITER:{limit:async()=>({success:true})}});
function request(body={amazonUserId:'qa-user',receiptId:'qa-receipt'},patch={}){
  const r=new Request('https://billing.invalid/v1/amazon/verify',{method:'POST',headers:{'content-type':'application/json','CF-Connecting-IP':'192.0.2.1'},body:JSON.stringify(body),...patch});
  Object.defineProperty(r,'cf',{value:{colo:'QA'}});return r;
}
const handler=(options={})=>createWorkerHandler({getVerifier:()=>async()=>({lease:'QA-only'}),...options});

test('shipped Android checkout readiness URL reaches the production Worker',async()=>{
  const configuration=JSON.parse(readFileSync(new URL('../../config/billing/production.json',import.meta.url),'utf8').replace(/^\uFEFF/,''));
  const android=readFileSync(new URL('../../app/src/main/java/com/trailnest/app/billing/AmazonBillingRuntime.kt',import.meta.url),'utf8');
  // Bind this contract test to the actual client derivation, rather than only
  // testing a separately configured health URL that the client never requests.
  assert.match(android,/URL\(endpoint\.removeSuffix\("verify"\) \+ "ready"\)/);
  const checkoutReady=configuration.verificationUrl.replace(/verify$/,'ready');
  assert.equal(configuration.readinessUrl,checkoutReady);
  const h=handler();
  assert.equal((await h(new Request(checkoutReady,{method:'HEAD'}),env())).status,204);
  assert.equal((await h(new Request(checkoutReady,{method:'HEAD'}),{})).status,503);
  assert.equal((await h(new Request(checkoutReady),env())).status,405);
});
test('Worker routes, transport, method, content type and runtime boundary',async()=>{
  const h=handler(),e=env();
  for(const [r,status]of [[new Request('http://billing.invalid/ready',{method:'HEAD'}),426],
    [new Request('https://billing.invalid/unknown'),404],[new Request('https://billing.invalid/ready'),405],
    [request({}, {headers:{'content-type':'text/plain'}}),415],
    [new Request('https://billing.invalid/v1/amazon/verify',{method:'POST',headers:{'content-type':'application/json','CF-Connecting-IP':'192.0.2.1'},body:'{}'}),400]]){
    assert.equal((await h(r,e)).status,status);
  }
});
test('readiness fails closed without key or rate binding and never verifies receipts',async()=>{
  let calls=0;const h=handler({getVerifier:()=>{calls++;return async()=>{throw Error('not readiness');};}});
  const r=()=>new Request('https://billing.invalid/ready',{method:'HEAD'});
  assert.equal((await h(r(),{})).status,503);assert.equal(calls,0);
  assert.equal((await h(r(),env())).status,204);assert.equal(calls,1);
  assert.equal((await createWorkerHandler()(r(),env())).status,503);
});
test('rate denial or binding failure never contacts Amazon; addresses are hashed',async()=>{
  let calls=0;const h=handler({getVerifier:()=>async()=>{calls++;return {};}});
  for(const mode of ['deny','throw']){
    const e={VERIFY_RATE_LIMITER:{limit:async({key})=>{assert.match(key,/^[a-f0-9]{64}$/);if(mode==='throw')throw Error('secret');return {success:false};}}};
    assert.equal((await h(request(),e)).status,mode==='deny'?429:503);
  }assert.equal(calls,0);
});
test('bounded streaming bodies, malformed JSON and noncacheable responses',async()=>{
  const h=handler(),e=env();
  assert.equal((await h(request({receiptId:'x'.repeat(8192)}),e)).status,413);
  assert.equal((await h(request({}, {body:'{'}),e)).status,400);
  const result=await h(request(),e);assert.equal(result.status,200);
  assert.equal(result.headers.get('cache-control'),'no-store');assert.deepEqual(await result.json(),{lease:'QA-only'});
});
test('concurrency cap and error sanitisation',async()=>{
  let release,started=0;const pending=new Promise(r=>{release=r;});
  const h=handler({getVerifier:()=>async()=>{started++;await pending;return {};}}),e=env();
  const requests=Array.from({length:8},()=>h(request(),e));
  while(started<8)await new Promise(r=>setImmediate(r));
  assert.equal((await h(request(),e)).status,429);release();await Promise.all(requests);
  for(const error of [new VerificationError(403),Error('private RVS URL')]){
    const r=await handler({getVerifier:()=>async()=>{throw error;}})(request(),env());
    assert.equal(r.status,error instanceof VerificationError?403:503);assert(!((await r.text()).includes('private')));
  }
});
test('production loader fixes identity and rejects missing/invalid secrets',()=>{
  const keys=generateKeyPairSync('rsa',{modulusLength:2048});
  const e={BILLING_SIGNING_KEY_BASE64:Buffer.from(keys.privateKey.export({format:'pem',type:'pkcs8'})).toString('base64'),AMAZON_RVS_SHARED_SECRET:'QA-only'};
  assert.equal(typeof productionVerifier(e),'function');
  for(const patch of [{BILLING_SIGNING_KEY_BASE64:'bad!'},{AMAZON_RVS_SHARED_SECRET:''}])assert.throws(()=>productionVerifier({...e,...patch}));
});
test('production loader grants only the dedicated production annual SKU',async(t)=>{
  const keys=generateKeyPairSync('rsa',{modulusLength:2048});
  const now=Date.now();
  let receipt={receiptId:'qa-receipt',productId:'com.trailnest.premium.production',
    termSku:'com.trailnest.premium.production.annual',productType:'SUBSCRIPTION',
    purchaseDate:now-1000,renewalDate:now+3600000,cancelDate:null,gracePeriodEndDate:null,
    testTransaction:false,betaProduct:false,baseReceipts:null};
  t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify(receipt)));
  const verify=productionVerifier({BILLING_SIGNING_KEY_BASE64:Buffer.from(keys.privateKey.export({format:'pem',type:'pkcs8'})).toString('base64'),AMAZON_RVS_SHARED_SECRET:'QA-only'});
  const body={amazonUserId:'qa-user',receiptId:'qa-receipt'};
  const result=await verify(body);
  const payload=Buffer.from(result.lease.split('.')[0],'base64url').toString('utf8');
  assert.match(payload,/\nproduction\n/);
  assert.match(payload,/\ncom\.trailnest\.premium\.production\ncom\.trailnest\.premium\.production\.annual\n/);
  for(const patch of [{productId:'com.trailnest.premium',termSku:'com.trailnest.premium_term'},
    {testTransaction:true},{betaProduct:true}]){
    const original=receipt;receipt={...receipt,...patch};
    await assert.rejects(verify(body),error=>error instanceof VerificationError&&error.status===403);
    receipt=original;
  }
});
