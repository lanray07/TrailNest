import {isIP} from 'node:net';
import {createVerifier, VerificationError, hash} from './verifier.mjs';
import {createWebCryptoSigner} from './cloudflare-signing.mjs';
const signLease=createWebCryptoSigner();

const headers={'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'};
const json=(status,body)=>new Response(JSON.stringify(body),{status,headers});

// A separate production entrypoint. No LAT identity, URL or environment is used.
export function productionVerifier(env){
  const encoded=env.BILLING_SIGNING_KEY_BASE64;
  if(typeof encoded!=='string'||encoded.length>16384||!/^[A-Za-z0-9+/]+={0,2}$/.test(encoded))throw Error('Signing configuration incomplete');
  const privateKey=Buffer.from(encoded,'base64');
  if(privateKey.toString('base64')!==encoded)throw Error('Signing configuration invalid');
  return createVerifier({environment:'production',applicationId:'com.trailnest.app',parentSku:'com.trailnest.premium.production',
    termSku:'com.trailnest.premium.production.annual',sharedSecret:env.AMAZON_RVS_SHARED_SECRET,privateKey},{signImpl:signLease});
}

export function createWorkerHandler({getVerifier=productionVerifier}={}){
  // Cache per binding object, so rotation creates a new verifier without mixing secrets.
  const verifiers=new WeakMap(); let inFlight=0;
  return async(request,env)=>{
    const url=new URL(request.url);
    if(url.protocol!=='https:')return json(426,{error:'https_required'});
    if(!['/ready','/v1/amazon/verify'].includes(url.pathname)||url.search)return json(404,{error:'not_found'});
    const ready=url.pathname==='/ready';
    if(request.method!==(ready?'HEAD':'POST'))return json(405,{error:'method_not_allowed'});
    if(!ready&&request.headers.get('content-type')?.split(';')[0].trim()!=='application/json')return json(415,{error:'json_required'});
    let verify;
    try{
      if(typeof env.VERIFY_RATE_LIMITER?.limit!=='function')throw Error('Missing rate binding');
      verify=verifiers.get(env);
      if(!verify){verify=getVerifier(env);verifiers.set(env,verify);}
    }catch{return ready?new Response(null,{status:503,headers}):json(503,{error:'verification_unavailable'});}
    if(ready)return new Response(null,{status:204,headers});
    // cf is populated by the Workers runtime, never trust a client header alone.
    const ip=request.headers.get('CF-Connecting-IP');
    if(!request.cf||!ip||!isIP(ip))return json(400,{error:'invalid_proxy_address'});
    try{
      const limit=await env.VERIFY_RATE_LIMITER.limit({key:hash(ip)});
      if(limit?.success!==true)return json(429,{error:'try_later'});
    }catch{return json(503,{error:'verification_unavailable'});}
    if(inFlight>=8)return json(429,{error:'try_later'});
    inFlight++;
    try{
      const length=request.headers.get('content-length');
      if(length!==null&&(!/^\d+$/.test(length)||Number(length)>8192))return json(413,{error:'request_too_large'});
      const reader=request.body?.getReader();if(!reader)return json(400,{error:'invalid_request'});
      let size=0,timedOut=false;const chunks=[];
      const timer=setTimeout(()=>{timedOut=true;reader.cancel().catch(()=>{});},5000);
      try{
        while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;
          if(size>8192)return json(413,{error:'request_too_large'});chunks.push(Buffer.from(value));}
      }finally{clearTimeout(timer);await reader.cancel().catch(()=>{});}
      if(timedOut)return json(400,{error:'invalid_request'});
      let body;try{body=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{return json(400,{error:'invalid_request'});}
      return json(200,await verify(body));
    }catch(error){
      const status=error instanceof VerificationError?error.status:503;
      return json(status,{error:status===403?'not_entitled':status===400?'invalid_request':'verification_unavailable'});
    }finally{inFlight--;}
  };
}

const handle=createWorkerHandler();
export default {fetch:handle};
