import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {isIP} from 'node:net';
import {createVerifier,VerificationError} from './verifier.mjs';

const loopback=address=>['127.0.0.1','::1','::ffff:127.0.0.1'].includes(address);
/** Native receipt/user pairs are bearer proof, checked by RVS, never an app login.
 * This service grants only account-bound local leases; it is not a content API. */
export function createHandler(verify,{trustedLoopbackProxy=false,now=Date.now}={}){
  const limits=new Map();let inFlight=0;
  return async(req,res)=>{
    const reply=(status,body)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(body));};
    if(!req.socket.encrypted&&!(trustedLoopbackProxy&&loopback(req.socket.remoteAddress)&&req.headers['x-forwarded-proto']==='https'))return reply(426,{error:'https_required'});
    if(req.url==='/v1/amazon/ready'&&req.method==='HEAD'){res.writeHead(204,{'Cache-Control':'no-store'});return res.end();}
    if(req.url!=='/v1/amazon/verify'||req.method!=='POST')return reply(404,{error:'not_found'});
    if(req.headers['content-type']?.split(';')[0]!=='application/json')return reply(415,{error:'json_required'});
    const forwarded=trustedLoopbackProxy&&loopback(req.socket.remoteAddress)?req.headers['x-forwarded-for']:undefined;
    if(forwarded!==undefined&&(typeof forwarded!=='string'||!isIP(forwarded)))return reply(400,{error:'invalid_proxy_address'});
    const time=now(),ip=forwarded??req.socket.remoteAddress;
    for(const [key,value]of limits)if(time-value.started>=60000)limits.delete(key);
    let limit=limits.get(ip);
    if(!limit){if(limits.size>=10000)return reply(429,{error:'try_later'});limit={started:time,count:0};limits.set(ip,limit);}
    if(++limit.count>20||inFlight>=8)return reply(429,{error:'try_later'});
    inFlight++;
    try {
      let size=0;const chunks=[];
      for await(const chunk of req){size+=chunk.length;if(size>8192)return reply(413,{error:'request_too_large'});chunks.push(chunk);}
      let request;try{request=JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch{return reply(400,{error:'invalid_request'});}
      return reply(200,await verify(request));
    }catch(error){const status=error instanceof VerificationError?error.status:503;return reply(status,{error:status===403?'not_entitled':status===400?'invalid_request':'verification_unavailable'});}
    finally {inFlight--;}
  };
}

export async function startServer(env=process.env){
  const verify=createVerifier({environment:env.AMAZON_RVS_ENV??'production',parentSku:env.AMAZON_PARENT_SKU,termSku:env.AMAZON_ANNUAL_SKU,applicationId:env.BILLING_APPLICATION_ID,
    sharedSecret:env.AMAZON_RVS_SHARED_SECRET,privateKey:await readFile(env.BILLING_SIGNING_KEY_FILE,'utf8')});
  const server=http.createServer(createHandler(verify,{trustedLoopbackProxy:env.BILLING_TRUST_LOOPBACK_TLS_PROXY==='true'}));
  server.requestTimeout=10000;server.headersTimeout=5000;server.maxHeadersCount=40;
  const port=Number(env.PORT??8080);if(!Number.isInteger(port)||port<1||port>65535)throw new Error('Invalid listener');
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});return server;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  startServer().then(()=>console.log('Billing verifier listening on loopback; TLS proxy required.')).catch(()=>{console.error('Billing verifier not started: configuration missing or invalid.');process.exitCode=1;});
}
