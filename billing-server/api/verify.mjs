import {hostedHandler} from '../vercel-handler.mjs';
export function POST(request){return hostedHandler(request,'verify');}
