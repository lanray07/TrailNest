import {test} from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import {DatabaseSync} from 'node:sqlite';
import {extractResources,pluralErrors} from '../localisation-resources.mjs';
test('extracts strings and plurals across resource files',()=>{
  const resources=extractResources(['<resources><string name="one">Hello</string></resources>','<resources><string name="two">World</string><plurals name="count"><item quantity="one">%1$d walk</item><item quantity="other">%1$d walks</item></plurals></resources>']);
  assert.deepEqual(Object.keys(resources.strings),['one','two']);assert.equal(resources.plurals.count.other,'%1$d walks');
  assert.throws(()=>extractResources(['<plurals name="broken"><item quantity="one">One</item></plurals>']),/other/);
});
test('plural validation checks language categories and placeholders',()=>{
  const source={other:'%1$d walks'};
  assert.deepEqual(pluralErrors(source,{forms:{other:'%1$d 歩行'}},'ja'),[]);
  assert.deepEqual(pluralErrors(source,{forms:{one:'%1$d walk',other:'%1$d walks'}},'en-GB'),[]);
  assert.ok(pluralErrors(source,{forms:{one:'one',other:'%1$d walks'}},'en-GB').includes('one placeholders'));
  assert.ok(pluralErrors(source,{forms:{other:'%1$d'}},'ar').includes('missing few'));
});
test('actual migration SQL preserves old walks/routes and cascades photo rows',()=>{
  const source=fs.readFileSync(new URL('../../app/src/main/java/com/trailnest/app/data/TrailDatabase.kt',import.meta.url),'utf8');
  const statements=[...source.matchAll(/db\.execSQL\("([^"]+)"\)/g)].map(m=>m[1]);assert.equal(statements.length,2);
  const db=new DatabaseSync(':memory:');
  try {
    db.exec('PRAGMA foreign_keys=ON; CREATE TABLE walks(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,date INTEGER NOT NULL,metres REAL NOT NULL,minutes INTEGER NOT NULL,notes TEXT NOT NULL,rating INTEGER NOT NULL,favourite INTEGER NOT NULL); CREATE TABLE routes(id TEXT PRIMARY KEY,geometry TEXT);');
    db.prepare('INSERT INTO walks VALUES(?,?,?,?,?,?,?,?)').run('kept','Existing entry',123,1000,20,'keep notes',4,1);
    db.prepare('INSERT INTO routes VALUES(?,?)').run('route','geometry kept');
    for(const sql of statements)db.exec(sql);
    assert.equal(db.prepare('SELECT notes FROM walks').get().notes,'keep notes');
    assert.equal(db.prepare('SELECT geometry FROM routes').get().geometry,'geometry kept');
    db.prepare('INSERT INTO walk_photos VALUES(?,?,?)').run('photo','kept','private.jpg');
    assert.throws(()=>db.prepare('INSERT INTO walk_photos VALUES(?,?,?)').run('orphan','missing','photo.jpg'));
    db.prepare('DELETE FROM walks WHERE id=?').run('kept');assert.equal(db.prepare('SELECT COUNT(*) AS count FROM walk_photos').get().count,0);
  } finally { db.close(); }
});
