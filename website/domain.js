export const countries = [['GB','United Kingdom'],['US','United States'],['CA','Canada'],['DE','Germany'],['FR','France'],['IT','Italy'],['ES','Spain'],['NL','Netherlands'],['JP','Japan'],['AU','Australia']];
export const kit = ['Drinking water','Suitable footwear','Weather-appropriate clothing','Charged phone','Power bank','Snacks','Offline route information','Check current conditions','Emergency contact plan','Dog lead, if relevant','First-aid essentials'];
export function distanceLabel(metres, units='imperial') {return new Intl.NumberFormat(undefined,{maximumFractionDigits:1,minimumFractionDigits:1}).format(metres/(units==='metric'?1000:1609.344))+(units==='metric'?' km':' mi');}
export function validateWalk({title,distance,minutes}) {const d=Number(distance),m=Number(minutes);return title.trim().length>0&&Number.isFinite(d)&&d>0&&d<=1000&&Number.isInteger(m)&&m>=1&&m<=10080;}
export function metresBetween(a,b) { const r=x=>x*Math.PI/180;const h=Math.sin(r(b.lat-a.lat)/2)**2+Math.cos(r(a.lat))*Math.cos(r(b.lat))*Math.sin(r(b.lon-a.lon)/2)**2;return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h))); }
export function routeLength(points) {return points.slice(1).reduce((total,p,i)=>total+metresBetween(points[i],p),0);}
export function validPoint(p) {return Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180;}
export function escapeHtml(value) {return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
