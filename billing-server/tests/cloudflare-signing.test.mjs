import {test} from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,verify,createPrivateKey} from 'node:crypto';
import {createWebCryptoSigner} from '../cloudflare-signing.mjs';

test('Cloudflare Web Crypto signatures verify with the existing RSA-SHA256 contract across keys',async()=>{
  const signer=createWebCryptoSigner();const body=Buffer.from('QA-only encoded lease');
  const first=generateKeyPairSync('rsa',{modulusLength:2048});
  const second=generateKeyPairSync('rsa',{modulusLength:2048});
  const signature=await signer(body,first.privateKey);
  assert.equal(verify('RSA-SHA256',body,first.publicKey,signature),true);
  assert.equal(verify('RSA-SHA256',body,second.publicKey,signature),false);
  assert.equal(verify('RSA-SHA256',Buffer.from('modified'),first.publicKey,signature),false);
  assert.equal(verify('RSA-SHA256',body,second.publicKey,await signer(body,second.privateKey)),true);
  assert.deepEqual(await signer(body,first.privateKey),signature);
});

test('failed Web Crypto import retries and never returns an unsigned grant',async()=>{
  const pair=generateKeyPairSync('rsa',{modulusLength:2048});let attempts=0;
  const signer=createWebCryptoSigner({
    importKey(...args){if(++attempts===1)return Promise.reject(Error('QA import failure'));return crypto.subtle.importKey(...args);},
    sign(...args){return crypto.subtle.sign(...args);}
  });
  const key=createPrivateKey(pair.privateKey.export({format:'pem',type:'pkcs8'}));
  await assert.rejects(signer(Buffer.from('QA'),key));
  const signature=await signer(Buffer.from('QA'),key);
  assert.equal(attempts,2);assert.equal(verify('RSA-SHA256',Buffer.from('QA'),pair.publicKey,signature),true);
});
