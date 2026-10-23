import {hostedHandler} from '../vercel-handler.mjs';
export function HEAD(request){return hostedHandler(request,'ready');}
