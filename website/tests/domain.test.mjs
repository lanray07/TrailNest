import {test} from 'node:test';import assert from 'node:assert/strict';
import {validateWalk,metresBetween,validPoint,escapeHtml,routeLength,countries} from '../domain.js';
test('journal rejects nonfinite, empty and unreasonable entries',()=>{for(const data of [{title:'',distance:1,minutes:30},{title:'Walk',distance:Infinity,minutes:30},{title:'Walk',distance:-1,minutes:30},{title:'Walk',distance:2,minutes:1.5}])assert.equal(validateWalk(data),false);assert.equal(validateWalk({title:'Morning walk',distance:4.2,minutes:65}),true);});
test('one longitude degree at equator is about 111.2 km',()=>assert.ok(Math.abs(metresBetween({lat:0,lon:0},{lat:0,lon:1})-111195)<20));
test('geometry length sums segments and validates coordinates',()=>{assert.equal(routeLength([{lat:0,lon:0},{lat:0,lon:0}]),0);assert.equal(validPoint({lat:91,lon:0}),false);assert.equal(validPoint({lat:NaN,lon:0}),false);});
test('untrusted titles cannot insert HTML',()=>assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;'));
test('country codes are unique',()=>assert.equal(new Set(countries.map(c=>c[0])).size,countries.length));
