import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,verify} from 'node:crypto';
import http from 'node:http';
import {createVerifier,receiptExpiry,hash,MAX_LEASE_MS} from '../verifier.mjs';
import {createHandler,startServer} from '../server.mjs';

const keys=generateKeyPairSync('rsa',{modulusLength:2048});
const key=keys.privateKey.export({format:'pem',type:'pkcs8'});
const now=1800000000000;
const config={environment:'production',parentSku:'qa.parent',termSku:'qa.annual',applicationId:'com.trailnest.app',sharedSecret:'QA-secret:/not-live',privateKey:key,leaseMs:MAX_LEASE_MS};
const request={amazonUserId:'QA-user',receiptId:'QA-receipt'};
const receipt={receiptId:request.receiptId,productId:config.parentSku,termSku:config.termSku,productType:'SUBSCRIPTION',testTransaction:false,betaProduct:false,purchaseDate:now-10000,cancelDate:null,renewalDate:now+100000000,gracePeriodEndDate:null};
const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
const make=(overrides={},fetchImpl=async()=>response(receipt))=>createVerifier({...config,...overrides},{fetchImpl,now:()=>now});
const denied=error=>error.status===403;

test('RVS-only success creates a verifiable account/receipt/package/SKU bound lease',async()=>{
  let url,options;
  const result=await make({},async(u,o)=>{url=u;options=o;return response(receipt);})(request);
  const [payload,signature]=result.lease.split('.');
  assert(verify('RSA-SHA256',Buffer.from(payload,'ascii'),keys.publicKey,Buffer.from(signature,'base64url')));
  assert.deepEqual(Buffer.from(payload,'base64url').toString().split('\n'),['TN1',config.applicationId,'production',hash(request.amazonUserId),hash(request.receiptId),config.parentSku,config.termSku,String(now),String(now+MAX_LEASE_MS)]);
  assert(url.startsWith('https://appstore-sdk.amazon.com/version/1.0/verifyReceiptId/'));
  assert(url.includes('QA-secret%3A%2Fnot-live'));assert.equal(options.redirect,'manual');assert(options.signal);
  assert(!result.lease.includes(request.amazonUserId));
});
test('sandbox and LAT are explicitly isolated from production',async()=>{
  let url;
  const result=await make({environment:'sandbox'},async u=>{url=u;return response({...receipt,testTransaction:true});})(request);
  assert(url.includes('/sandbox/version/1.0/'));assert.equal(Buffer.from(result.lease.split('.')[0],'base64url').toString().split('\n')[2],'sandbox');
  await assert.rejects(make({},async()=>response({...receipt,testTransaction:true}))(request),denied);
  await assert.rejects(make({},async()=>response({...receipt,betaProduct:true}))(request),denied);
  await make({environment:'lat'},async()=>response({...receipt,betaProduct:true,testTransaction:true}))(request);
});
test('refunds, expiry, mismatched IDs/products/terms/types and malformed receipts cannot grant',async()=>{
  for(const patch of [{cancelDate:now},{cancelDate:now-1},{renewalDate:now-1},{receiptId:'other'}, {productId:'other'},{termSku:'other'},{productType:'ENTITLED'},{purchaseDate:now+1},{testTransaction:'false'},{betaProduct:null},{cancelDate:undefined},{renewalDate:-1},{baseReceipts:['other']}]){
    await assert.rejects(make({},async()=>response({...receipt,...patch}))(request),denied);
  }
});
test('future cancellation caps access and grace extends a past renewal without overriding refund',()=>{
  assert.equal(receiptExpiry({...receipt,cancelDate:now+1000},request,config,now),now+1000);
  assert.equal(receiptExpiry({...receipt,renewalDate:now-1,gracePeriodEndDate:now+2000},request,config,now),now+2000);
  assert.throws(()=>receiptExpiry({...receipt,cancelDate:now,gracePeriodEndDate:now+2000},request,config,now),denied);
  assert.equal(receiptExpiry({...receipt,renewalDate:null,autoRenewing:false},request,config,now),now+MAX_LEASE_MS);
});
test('RVS error mapping denies revoked receipts; outage/credentials/throttling/redirect remain retryable',async()=>{
  for(const status of [400,410,497])await assert.rejects(make({},async()=>response({},status))(request),denied);
  for(const status of [429,496,500,302])await assert.rejects(make({},async()=>response({},status))(request),e=>e.status===503&&!e.message.includes(config.sharedSecret));
  await assert.rejects(make({},async()=>{throw Error('sensitive upstream URL '+config.sharedSecret);})(request),e=>e.status===503&&!e.message.includes(config.sharedSecret));
});
test('untrusted configuration fields and oversized/malformed RVS replies cannot grant',async()=>{
  for(const body of [{...request,environment:'sandbox'},{...request,sku:'qa.parent'},{amazonUserId:'..',receiptId:'x'}, {...request,receiptId:'x\ny'},null])await assert.rejects(make()(body),e=>e.status===400);
  await assert.rejects(make({},async()=>new Response('{'))(request),e=>e.status===503);
  await assert.rejects(make({},async()=>new Response('x'.repeat(33000)))(request),e=>e.status===503);
  for(const patch of [{environment:'unknown'},{leaseMs:MAX_LEASE_MS+1},{leaseMs:0},{parentSku:''},{sharedSecret:''}])assert.throws(()=>make(patch));
});

async function fixture(options,fn){
  const server=http.createServer(createHandler(make(),options));server.requestTimeout=1000;
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const url=`http://127.0.0.1:${server.address().port}/v1/amazon/verify`;
  try{await fn(url);}finally{await new Promise(resolve=>server.close(resolve));}
}
const post=(url,body=request,headers={})=>fetch(url,{method:'POST',headers:{'Content-Type':'application/json','x-forwarded-proto':'https',...headers},body:JSON.stringify(body)});
test('actual HTTP boundary requires TLS, rejects wrong routes/content/body, and issues only signed leases',async()=>{
  await fixture({},async url=>assert.equal((await post(url)).status,426));
  await fixture({trustedLoopbackProxy:true},async url=>{
    const result=await post(url);assert.equal(result.status,200);assert.equal(result.headers.get('cache-control'),'no-store');assert((await result.json()).lease);
    assert.equal((await fetch(url.replace('verify','ready'),{method:'HEAD',headers:{'x-forwarded-proto':'https'}})).status,204);
    assert.equal((await post(url,request,{'Content-Type':'text/plain'})).status,415);
    assert.equal((await post(url,{...request,receiptId:'x'.repeat(9000)})).status,413);
    assert.equal((await post(url,{}, {'x-forwarded-proto':'http'})).status,426);
    assert.equal((await post(url+'/other')).status,404);
    assert.equal((await post(url,{...request,environment:'sandbox'})).status,400);
    assert.equal((await post(url,request,{'x-forwarded-for':'untrusted, chain'})).status,400);
  });
});
test('HTTP rate limit bounds upstream calls and resets after its window',async()=>{
  let time=now;
  await fixture({trustedLoopbackProxy:true,now:()=>time},async url=>{
    for(let i=0;i<20;i++)assert.equal((await post(url)).status,200);
    assert.equal((await post(url)).status,429);time+=60000;assert.equal((await post(url)).status,200);
  });
});
test('unconfigured server refuses to start',async()=>{await assert.rejects(startServer({}));});

test('sandbox diagnostics expose fixed status/shape fields without receipt identities or secrets',async()=>{
  const events=[];
  const verify=createVerifier({...config,environment:'sandbox'},{now:()=>now,audit:e=>events.push(e),fetchImpl:async()=>response({...receipt,termSku:'wrong'})});
  await assert.rejects(verify(request),denied);
  assert.equal(events[0].event,'receipt_rejected');assert.equal(events[0].termMatches,false);
  const serialized=JSON.stringify(events);
  for(const value of [request.amazonUserId,request.receiptId,config.sharedSecret,'wrong'])assert(!serialized.includes(value));
  const production=[];
  await createVerifier(config,{now:()=>now,audit:e=>production.push(e),fetchImpl:async()=>response(receipt)})(request);
  assert.deepEqual(production,[]);
});
