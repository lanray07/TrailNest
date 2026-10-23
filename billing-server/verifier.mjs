import {createHash, createPrivateKey, sign} from 'node:crypto';

export const MAX_LEASE_MS=6*60*60*1000;
export const hash=value=>createHash('sha256').update(value,'utf8').digest('hex');
export class VerificationError extends Error {
  constructor(status){super('Receipt verification unavailable');this.status=status;}
}
const identifier=value=>typeof value==='string'&&value.length>0&&value.length<=2048&&!/[\s\x00-\x1f\x7f]/.test(value)&&value!=='.'&&value!=='..';
const sku=value=>typeof value==='string'&&/^[A-Za-z0-9._-]{1,200}$/.test(value);

/** All trust settings are server supplied; requests cannot choose a SKU or environment. */
export function validateConfig(config){
  if(!['production','sandbox','lat'].includes(config.environment)||!sku(config.parentSku)||!sku(config.termSku)||
    !sku(config.applicationId)||!identifier(config.sharedSecret)||!Number.isSafeInteger(config.leaseMs??MAX_LEASE_MS)||
    (config.leaseMs??MAX_LEASE_MS)<=0||(config.leaseMs??MAX_LEASE_MS)>MAX_LEASE_MS)throw new Error('Invalid billing server configuration');
  const key=createPrivateKey(config.privateKey);
  if(key.asymmetricKeyType!=='rsa'||key.asymmetricKeyDetails.modulusLength<2048)throw new Error('RSA signing key required');
  return {...config,privateKey:key,leaseMs:config.leaseMs??MAX_LEASE_MS};
}

export function receiptExpiry(receipt,request,config,now){
  if(!receipt||typeof receipt!=='object'||Array.isArray(receipt)||receipt.receiptId!==request.receiptId||
    receipt.productId!==config.parentSku||receipt.termSku!==config.termSku||receipt.productType!=='SUBSCRIPTION'||
    typeof receipt.testTransaction!=='boolean'||typeof receipt.betaProduct!=='boolean'||
    !Number.isSafeInteger(receipt.purchaseDate)||receipt.purchaseDate<=0||receipt.purchaseDate>now)throw new VerificationError(403);
  if(receipt.baseReceipts!=null&&(!Array.isArray(receipt.baseReceipts)||receipt.baseReceipts.length>0))throw new VerificationError(403);
  if(config.environment==='production'&&(receipt.testTransaction||receipt.betaProduct))throw new VerificationError(403);
  const dates=['cancelDate','renewalDate','gracePeriodEndDate'];
  for(const name of dates)if(receipt[name]!==null&&(!Number.isSafeInteger(receipt[name])||receipt[name]<receipt.purchaseDate))throw new VerificationError(403);
  if(receipt.cancelDate!==null&&receipt.cancelDate<=now)throw new VerificationError(403);
  let expiry=now+config.leaseMs;
  if(receipt.cancelDate!==null)expiry=Math.min(expiry,receipt.cancelDate);
  // A grace period may extend a past renewal. Turning off auto-renew does not
  // revoke a still-active receipt; Amazon can return no next renewal date.
  const horizon=Math.max(receipt.renewalDate??0,receipt.gracePeriodEndDate??0);
  if(horizon>0)expiry=Math.min(expiry,horizon);
  if(expiry<=now)throw new VerificationError(403);
  return expiry;
}

async function boundedJson(response){
  if(!response.body)throw new VerificationError(503);
  const reader=response.body.getReader();let bytes=0;const chunks=[];
  try { while(true){const {done,value}=await reader.read();if(done)break;bytes+=value.byteLength;if(bytes>32768)throw new VerificationError(503);chunks.push(Buffer.from(value));} }
  finally { await reader.cancel().catch(()=>{}); }
  try {return JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{throw new VerificationError(503);}
}

export function createVerifier(rawConfig,{fetchImpl=fetch,now=Date.now,audit=()=>{}}={}){
  const config=validateConfig(rawConfig);
  return async request=>{
    if(!request||Array.isArray(request)||Object.keys(request).sort().join(',')!=='amazonUserId,receiptId'||
      !identifier(request.amazonUserId)||!identifier(request.receiptId))throw new VerificationError(400);
    const segment=value=>encodeURIComponent(value);
    const base='https://appstore-sdk.amazon.com'+(config.environment==='sandbox'?'/sandbox':'');
    const url=`${base}/version/1.0/verifyReceiptId/developer/${segment(config.sharedSecret)}/user/${segment(request.amazonUserId)}/receiptId/${segment(request.receiptId)}`;
    let response,receipt;
    // Never log errors containing this URL: RVS includes the secret in its path.
    try {
      response=await fetchImpl(url,{method:'GET',redirect:'manual',signal:AbortSignal.timeout(5000),headers:{Accept:'application/json'}});
      if(config.environment==='sandbox'&&response.status!==200)audit({event:'rvs_status',status:response.status});
      if(response.status===400||response.status===410||response.status===497)throw new VerificationError(403);
      if(response.status!==200)throw new VerificationError(503);
      receipt=await boundedJson(response);
    }catch(error){throw error instanceof VerificationError?error:new VerificationError(503);}
    const issuedAt=now();let expiresAt;
    try{expiresAt=receiptExpiry(receipt,request,config,issuedAt);}
    catch(error){
      // Fixed-shape sandbox diagnostics, never identities, receipt IDs or URLs.
      if(config.environment==='sandbox')audit({
        event:'receipt_rejected',receiptMatches:receipt?.receiptId===request.receiptId,
        parentMatches:receipt?.productId===config.parentSku,termMatches:receipt?.termSku===config.termSku,
        typeMatches:receipt?.productType==='SUBSCRIPTION',testFlagType:typeof receipt?.testTransaction,betaFlagType:typeof receipt?.betaProduct,
        purchaseDateValid:Number.isSafeInteger(receipt?.purchaseDate)&&receipt.purchaseDate>0&&receipt.purchaseDate<=issuedAt,
        cancelEffective:typeof receipt?.cancelDate==='number'&&receipt.cancelDate<=issuedAt,
        renewalPast:typeof receipt?.renewalDate==='number'&&receipt.renewalDate<=issuedAt,
        termKind:['Annual','Annually','1 Year','Monthly','1 Month'].includes(receipt?.term)?receipt.term:'other',
        autoRenewing:receipt?.autoRenewing===true,
        renewalOffsetMs:Number.isSafeInteger(receipt?.renewalDate)?receipt.renewalDate-issuedAt:null,
        dateShapes:['cancelDate','renewalDate','gracePeriodEndDate'].map(name=>receipt?.[name]===null?'null':typeof receipt?.[name])
      });
      throw error;
    }
    const payload=['TN1',config.applicationId,config.environment,hash(request.amazonUserId),hash(request.receiptId),config.parentSku,config.termSku,issuedAt,expiresAt].join('\n');
    const encoded=Buffer.from(payload,'utf8').toString('base64url');
    const signature=sign('RSA-SHA256',Buffer.from(encoded,'ascii'),config.privateKey).toString('base64url');
    return {lease:encoded+'.'+signature};
  };
}
