// Cloudflare's asynchronous Web Crypto path avoids the synchronous Node RSA
// cold-request cost. Keys remain private, non-extractable and cached per identity.
export function createWebCryptoSigner(subtle=globalThis.crypto.subtle){
  const keys=new WeakMap();
  return async(bytes,privateKey)=>{
    let pending=keys.get(privateKey);
    if(!pending){
      pending=subtle.importKey('pkcs8',privateKey.export({format:'der',type:'pkcs8'}),
        {name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
      keys.set(privateKey,pending);
      pending.catch(()=>{if(keys.get(privateKey)===pending)keys.delete(privateKey);});
    }
    return Buffer.from(await subtle.sign('RSASSA-PKCS1-v1_5',await pending,bytes));
  };
}
