import {cp,mkdir} from 'node:fs/promises';
await mkdir(new URL('./dist/',import.meta.url),{recursive:true});
for (const entry of ['index.html','app.js','domain.js','styles.css','assets','privacy','support','discover','robots.txt','sitemap.xml']) await cp(new URL(entry,import.meta.url),new URL('dist/'+entry,import.meta.url),{recursive:true});
console.log('Static website built in website/dist');
