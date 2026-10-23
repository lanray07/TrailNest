import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync} from 'node:crypto';
import {createHostedHandler,verifierFromEnvironment} from '../vercel-handler.mjs';
import {VerificationError} from '../verifier.mjs';

const request=(body={amazonUserId:'qa-user',receiptId:'qa-receipt'},headers={})=>new Request('https://qa.invalid/v1/amazon/verify',{method:'POST',headers:{'content-type':'application/json','x-vercel-forwarded-for':'192.0.2.1',...headers},body:JSON.stringify(body)});
const handler=(options={})=>createHostedHandler({platform:()=>true,getVerifier:()=>async()=>({lease:'QA-only'}),...options});

test('Vercel boundary requires platform, HTTPS, correct method and content type',async()=>{
  assert.equal((await handler({platform:()=>false})(request(),'verify')).status,426);
  assert.equal((await handler()(new Request('http://qa.invalid'),'ready')).status,426);
  assert.equal((await handler()(request(),'unknown')).status,404);
  assert.equal((await handler()(new Request('https://qa.invalid'),'verify')).status,405);
  assert.equal((await handler()(request({}, {'content-type':'text/plain'}),'verify')).status,415);
  assert.equal((await handler()(request({}, {'x-vercel-forwarded-for':'spoofed, chain'}),'verify')).status,400);
});

test('missing configuration is unavailable including HEAD readiness, without exposing errors',async()=>{
  const h=handler({getVerifier:()=>{throw Error('secret');}});
  const ready=await h(new Request('https://qa.invalid',{method:'HEAD'}),'ready');assert.equal(ready.status,503);assert.equal(await ready.text(),'');
  const result=await h(request(),'verify');assert.equal(result.status,503);assert(!await result.text().then(t=>t.includes('secret')));
});

test('bounded request body, no-store signed response and readiness',async()=>{
  let calls=0;const h=handler({getVerifier:()=>async body=>{calls++;assert.equal(body.receiptId,'qa-receipt');return {lease:'QA-only'};}});
  const ready=await h(new Request('https://qa.invalid',{method:'HEAD'}),'ready');assert.equal(ready.status,204);assert.equal(calls,0);
  const result=await h(request(),'verify');assert.equal(result.status,200);assert.equal(result.headers.get('cache-control'),'no-store');assert.deepEqual(await result.json(),{lease:'QA-only'});
  assert.equal((await h(request({receiptId:'x'.repeat(9000)}),'verify')).status,413);
  assert.equal((await h(new Request('https://qa.invalid',{method:'POST',headers:{'content-type':'application/json','x-vercel-forwarded-for':'192.0.2.1'},body:'{'}),'verify')).status,400);
  assert.equal(calls,1);
});

test('per-instance rate and concurrency limits bound upstream work',async()=>{
  let time=0;const h=handler({now:()=>time});for(let i=0;i<20;i++)assert.equal((await h(request(),'verify')).status,200);
  assert.equal((await h(request(),'verify')).status,429);time=60000;assert.equal((await h(request(),'verify')).status,200);
  let release;const pending=new Promise(resolve=>{release=resolve;});let started=0;
  const busy=handler({getVerifier:()=>async()=>{started++;await pending;return {lease:'QA-only'};}});
  const requests=Array.from({length:8},()=>busy(request(),'verify'));
  // Allow each bounded Web stream to reach the fake verifier.
  while(started<8)await new Promise(resolve=>setImmediate(resolve));
  assert.equal((await busy(request(),'verify')).status,429);release();assert((await Promise.all(requests)).every(r=>r.status===200));
});

test('denied receipts and upstream errors never leak details',async()=>{
  for(const error of [new VerificationError(403),Error('sensitive URL')]){
    const result=await handler({getVerifier:()=>async()=>{throw error;}})(request(),'verify');assert.equal(result.status,error instanceof VerificationError?403:503);assert(!await result.text().then(t=>t.includes('sensitive')));
  }
});

test('environment loader requires hosting, WAF confirmation, key and valid fixed bindings',async()=>{
  const keys=generateKeyPairSync('rsa',{modulusLength:2048});
  const env={VERCEL:'1',BILLING_EDGE_RATE_LIMIT_CONFIRMED:'true',BILLING_SIGNING_KEY_BASE64:Buffer.from(keys.privateKey.export({format:'pem',type:'pkcs8'})).toString('base64'),AMAZON_RVS_ENV:'sandbox',AMAZON_PARENT_SKU:'qa.parent',AMAZON_ANNUAL_SKU:'qa.parent_term',BILLING_APPLICATION_ID:'com.trailnest.app.draft',AMAZON_RVS_SHARED_SECRET:'QA-only'};
  assert.equal(typeof verifierFromEnvironment(env),'function');
  for(const patch of [{VERCEL:''},{BILLING_EDGE_RATE_LIMIT_CONFIRMED:''},{BILLING_SIGNING_KEY_BASE64:'bad!'},{AMAZON_RVS_ENV:'unknown'}])assert.throws(()=>verifierFromEnvironment({...env,...patch}));
});
