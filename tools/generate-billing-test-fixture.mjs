import fs from 'node:fs/promises';
import {generateKeyPairSync} from 'node:crypto';
import {createVerifier} from '../billing-server/verifier.mjs';

// Ephemeral test-only signing key: never persisted. The public fixture uses a
// fixed test clock, QA account/SKUs and sandbox audience; it cannot unlock releases.
const {privateKey,publicKey}=generateKeyPairSync('rsa',{modulusLength:2048});
const now=1800000000000;
const request={amazonUserId:'QA-user',receiptId:'QA-receipt'};
const verify=createVerifier({environment:'sandbox',applicationId:'com.trailnest.app.draft',parentSku:'qa.parent',termSku:'qa.annual',sharedSecret:'QA-only-not-a-live-secret',
  privateKey:privateKey.export({type:'pkcs8',format:'pem'}),leaseMs:10000},{now:()=>now,fetchImpl:async()=>new Response(JSON.stringify({receiptId:request.receiptId,productId:'qa.parent',termSku:'qa.annual',productType:'SUBSCRIPTION',testTransaction:true,betaProduct:false,purchaseDate:now-10000,cancelDate:null,renewalDate:now+100000,gracePeriodEndDate:null}))});
const target=new URL('../app/src/androidTest/assets/billing-fixture/',import.meta.url);await fs.mkdir(target,{recursive:true});
await fs.writeFile(new URL('public.pem',target),publicKey.export({type:'spki',format:'pem'}));
await fs.writeFile(new URL('lease.txt',target),(await verify(request)).lease+'\n');
console.log('Generated sandbox-only Android crypto fixture; private key discarded.');
