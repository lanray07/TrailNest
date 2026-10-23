import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {extractResources,escapeAndroid,placeholders,sourceHash,sourceIsCurrent,entryReviewed,androidQualifier,hardcodedUiStrings,pluralErrors} from '../localisation-resources.mjs';
const root=new URL('../../',import.meta.url),read=p=>JSON.parse(fs.readFileSync(new URL(p,root),'utf8'));
test('Android XML escaping round-trips punctuation, newlines and protected placeholders',()=>{
  const text='It\'s "private" & <local>\n%1$s · %2$d · \\path';
  assert.equal(extractResources([`<string name="test">${escapeAndroid(text)}</string>`]).strings.test,text);
  assert.deepEqual(extractResources(['<string name="brand" translatable="false">TrailNest</string>']).strings,{});
});
test('placeholder audit permits reordering and catches changed widths or argument types',()=>{
  assert.equal(placeholders('%1$s · %2$d'),placeholders('%2$d · %1$s'));
  assert.notEqual(placeholders('%1$s'),placeholders('%1$d'));
  assert.notEqual(placeholders('%02d'),placeholders('%d'));
  assert.equal(placeholders('100%% complete'),'');
});
test('source changes invalidate an otherwise reviewed draft and safety review is separate',()=>{
  const entry={text:'Bonjour',sourceHash:sourceHash('Hello'),reviewed:true,reviewer:'Human reviewer',reviewedAt:'2026-10-23'};
  assert.ok(entryReviewed(entry));assert.ok(sourceIsCurrent('Hello',entry));assert.ok(!sourceIsCurrent('Hello again',entry));
  assert.ok(!entryReviewed({...entry,reviewed:false}));assert.ok(!entryReviewed({...entry,requiresSafetyReview:true}));
  assert.ok(entryReviewed({...entry,requiresSafetyReview:true,safetyReviewer:'Safety reviewer'}));
  assert.ok(!entryReviewed({...entry,reviewedAt:'not a date'}));
});
test('BCP47 qualifiers retain region and script distinctions',()=>{
  assert.equal(androidQualifier('pt-BR'),'values-b+pt+BR');assert.equal(androidQualifier('zh-Hans'),'values-b+zh+Hans');
  assert.notEqual(androidQualifier('en-GB'),androidQualifier('en-US'));
});
test('UI scanner catches literal labels, descriptions and notices while ignoring stable IDs',()=>{
  assert.equal(hardcodedUiStrings('Text("Hello"); EmptyPanel("Empty", body); Image(painter, "Hills"); notice = "Failed"').length,4);
  assert.equal(hardcodedUiStrings('"My walks" -> JournalScreen(); Text(userNotes); Text(stringResource(R.string.title))').length,0);
});
test('all 19 draft bundles are complete, current, unreviewed and match preview resources',()=>{
  const registry=read('config/locales.json'),source=read('localisation/source.json'),plurals=read('localisation/plurals-source.json');
  assert.equal(registry.locales.length,19);
  for(const locale of registry.locales){
    assert.equal(locale.reviewed,false);
    const data=read(`localisation/${locale.tag}.json`);
    const rendered=extractResources([fs.readFileSync(new URL(`app/src/translationPreview/res/${androidQualifier(locale.tag)}/strings.xml`,root),'utf8')]);
    assert.deepEqual(Object.keys(rendered.strings).sort(),Object.keys(source).sort());
    assert.deepEqual(Object.keys(rendered.plurals).sort(),Object.keys(plurals).sort());
    for(const [key,value]of Object.entries(source)){
      const entry=data[key];assert.equal(entry.reviewed,false);assert.ok(sourceIsCurrent(value,entry),`${locale.tag} ${key}`);
      assert.equal(placeholders(entry.text),placeholders(value));assert.equal(rendered.strings[key],entry.text);
      for(const term of ['TrailNest','GPX','SHA-256','YYYY-MM-DD'])if(value.includes(term))assert.ok(entry.text.includes(term));
    }
    for(const [key,value]of Object.entries(plurals)){
      assert.equal(data[key].reviewed,false);assert.ok(sourceIsCurrent(value,data[key]));
      assert.deepEqual(pluralErrors(value,data[key],locale.tag),[]);assert.deepEqual(rendered.plurals[key],data[key].forms);
    }
  }
  // Draft resources must stay outside the normal APK's source set.
  assert.equal(fs.readdirSync(new URL('app/src/main/res/',root)).filter(name=>name.startsWith('values-b+')||/^values-[a-z]{2}(?:-r[A-Z]{2})?$/.test(name)).length,0);
});
