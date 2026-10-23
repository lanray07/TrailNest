import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.dirname(fileURLToPath(import.meta.url));
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.json':'application/json','.xml':'application/xml','.txt':'text/plain'};
http.createServer(async (req,res) => {
  try {
    let pathname = decodeURIComponent(new URL(req.url,'http://localhost').pathname);
    if (pathname.endsWith('/')) pathname += 'index.html';
    const target = path.resolve(root, '.' + pathname);
    if (!target.startsWith(root + path.sep)) throw Error('Invalid path');
    const data = await fs.readFile(target);
    res.writeHead(200, {'Content-Type':types[path.extname(target)] || 'application/octet-stream','X-Content-Type-Options':'nosniff','Referrer-Policy':'strict-origin-when-cross-origin'}); res.end(data);
  } catch { res.writeHead(404,{'Content-Type':'text/plain'}); res.end('Page not found'); }
}).listen(4173,'127.0.0.1',()=>console.log('TrailNest: http://127.0.0.1:4173'));
