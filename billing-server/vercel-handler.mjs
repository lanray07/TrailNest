import {isIP} from 'node:net';
import {createVerifier, VerificationError} from './verifier.mjs';

const json=(status,body)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});

export function verifierFromEnvironment(env){
  if(env.VERCEL!=='1'||env.BILLING_EDGE_RATE_LIMIT_CONFIRMED!=='true')throw Error('Hosting configuration incomplete');
  const encoded=env.BILLING_SIGNING_KEY_BASE64;
  if(typeof encoded!=='string'||encoded.length>16384||!encoded.length||!/^[A-Za-z0-9+/]+={0,2}$/.test(encoded))throw Error('Signing configuration incomplete');
  const privateKey=Buffer.from(encoded,'base64');
  if(privateKey.toString('base64')!==encoded)throw Error('Invalid signing configuration');
  return createVerifier({environment:env.AMAZON_RVS_ENV,parentSku:env.AMAZON_PARENT_SKU,termSku:env.AMAZON_ANNUAL_SKU,
    applicationId:env.BILLING_APPLICATION_ID,sharedSecret:env.AMAZON_RVS_SHARED_SECRET,privateKey},
    {audit:event=>{if(env.AMAZON_RVS_ENV==='sandbox')console.info('TrailNestRvs '+JSON.stringify(event));}});
}

/** Only used by Vercel entrypoints. Header trust is restricted to that platform.
 * Memory limits below are per instance; a configured WAF rate rule is required.
 * Missing configuration never reports ready or contacts Amazon. */
export function createHostedHandler({getVerifier,platform=()=>process.env.VERCEL==='1',now=Date.now}={}){
  const limits=new Map();let inFlight=0;
  return async(request,route)=>{
    if(!platform()||new URL(request.url).protocol!=='https:')return json(426,{error:'https_required'});
    if(!['ready','verify'].includes(route))return json(404,{error:'not_found'});
    if(request.method!==(route==='ready'?'HEAD':'POST'))return json(405,{error:'method_not_allowed'});
    if(route==='verify'&&request.headers.get('content-type')?.split(';')[0]!=='application/json')return json(415,{error:'json_required'});
    let verify;try{verify=getVerifier();}catch{return route==='ready'?new Response(null,{status:503,headers:{'Cache-Control':'no-store'}}):json(503,{error:'verification_unavailable'});}
    if(route==='ready')return new Response(null,{status:204,headers:{'Cache-Control':'no-store'}});
    const ip=request.headers.get('x-vercel-forwarded-for');
    if(!ip||!isIP(ip))return json(400,{error:'invalid_proxy_address'});
    const time=now();for(const [key,value]of limits)if(time-value.started>=60000)limits.delete(key);
    let limit=limits.get(ip);
    if(!limit){if(limits.size>=10000)return json(429,{error:'try_later'});limit={started:time,count:0};limits.set(ip,limit);}
    if(++limit.count>20||inFlight>=8)return json(429,{error:'try_later'});
    inFlight++;
    try{
      const length=request.headers.get('content-length');
      if(length!==null&&(!/^\d+$/.test(length)||Number(length)>8192))return json(413,{error:'request_too_large'});
      const reader=request.body?.getReader();if(!reader)return json(400,{error:'invalid_request'});
      const chunks=[];let size=0;
      let timedOut=false;
      const timer=setTimeout(()=>{timedOut=true;reader.cancel().catch(()=>{});},5000);let complete=false;
      try{while(true){const {done,value}=await reader.read();if(done){complete=true;break;}size+=value.byteLength;if(size>8192)return json(413,{error:'request_too_large'});chunks.push(Buffer.from(value));}}
      finally{clearTimeout(timer);await reader.cancel().catch(()=>{});}
      if(!complete||timedOut)return json(400,{error:'invalid_request'});
      let body;try{body=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{return json(400,{error:'invalid_request'});}
      return json(200,await verify(body));
    }catch(error){const status=error instanceof VerificationError?error.status:503;return json(status,{error:status===403?'not_entitled':status===400?'invalid_request':'verification_unavailable'});}
    finally{inFlight--;}
  };
}

let verifier;
export const hostedHandler=createHostedHandler({getVerifier:()=>verifier??=verifierFromEnvironment(process.env)});
