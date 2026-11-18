// Run with: node --test web/test/*.test.js
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const S = require('../sync.js');

const sampleText = fs.readFileSync(path.join(__dirname, '..', '..', 'sync', 'sample.json'), 'utf8');
const sample = () => S.parse(sampleText);
const byGuid = (articles, guid) => articles.find((a) => a.guid === guid);
const NOW = 1763470000000;

test('reads the shared sample file', () => {
  const file = sample();
  assert.equal(file.writtenBy, 'phone');
  assert.deepEqual(file.groups, ['News', 'Tech']);
  assert.equal(file.feeds.length, 2);
  assert.equal(file.articles.length, 3);
  assert.equal(byGuid(file.articles, 't1').saved, true);
});

test('refuses empty, foreign and newer files', () => {
  assert.throws(() => S.parse(''), S.SyncError);
  assert.throws(() => S.parse('{"format":"other"}'), S.SyncError);
  assert.throws(() => S.parse('not json'), S.SyncError);
  assert.throws(() => S.parse(JSON.stringify({ format: 'yoreader-sync', version: 99 })), /newer/);
});

test('a laptop change shows at once and is written to the file', () => {
  const file = sample();
  const pending = S.change({}, byGuid(file.articles, 'a1'), 'read', true, NOW);
  assert.equal(byGuid(S.view(file, pending), 'a1').read, true);
  assert.equal(S.needsWrite(file, pending), true);
  const written = S.applyPending(file, pending, NOW + 1);
  assert.equal(written.writtenBy, 'web');
  assert.equal(byGuid(written.articles, 'a1').read, true);
  assert.equal(byGuid(written.articles, 'a1').readAt, NOW);
  assert.equal(byGuid(written.articles, 'a2').read, true, 'other articles unchanged');
  assert.ok(JSON.stringify(written).startsWith('{"writtenBy":"web","writtenAt":' + (NOW + 1) + ','));
});

test('a newer change on the phone wins over an older laptop change', () => {
  const file = sample();
  const a2 = byGuid(file.articles, 'a2'); // read on the phone at 1763455000000
  const pending = S.change({}, a2, 'read', false, a2.readAt - 1000);
  assert.equal(byGuid(S.view(file, pending), 'a2').read, true);
  assert.equal(S.needsWrite(file, pending), false);
});

test('laptop changes survive until a phone write includes them', () => {
  const file = sample();
  const pending = S.change({}, byGuid(file.articles, 'a1'), 'read', true, NOW);
  // Our own write doesn't confirm anything: the phone may overwrite it.
  const ours = S.applyPending(file, pending, NOW + 1);
  assert.deepEqual(Object.keys(S.prune(pending, ours, NOW + 2)), ['https://example.org/feed.xml\na1']);
  // The phone wrote without having seen it: still pending, and to be written again.
  assert.equal(Object.keys(S.prune(pending, file, NOW + 2)).length, 1);
  assert.equal(S.needsWrite(file, S.prune(pending, file, NOW + 2)), true);
  // The phone wrote it back: confirmed, nothing left.
  const confirmed = JSON.parse(JSON.stringify(ours));
  confirmed.writtenBy = 'phone';
  assert.deepEqual(S.prune(pending, confirmed, NOW + 3), {});
});

test('read and saved are kept apart', () => {
  const file = sample();
  const t1 = byGuid(file.articles, 't1');
  let pending = S.change({}, t1, 'saved', false, NOW);
  pending = S.change(pending, t1, 'read', false, NOW + 1);
  const shown = byGuid(S.view(file, pending), 't1');
  assert.equal(shown.saved, false);
  assert.equal(shown.read, false);
  // The phone confirms "saved" only: "read" stays pending.
  const phone = sample();
  byGuid(phone.articles, 't1').saved = false;
  byGuid(phone.articles, 't1').savedAt = NOW;
  const left = S.prune(pending, phone, NOW + 2);
  assert.deepEqual(left[S.key(t1)], { read: false, readAt: NOW + 1 });
});

test('changes for articles the phone dropped, or very old ones, are forgotten', () => {
  const file = sample();
  const gone = { feed: 'https://example.org/feed.xml', guid: 'old' };
  let pending = S.change({}, gone, 'read', true, NOW);
  pending = S.change(pending, byGuid(file.articles, 'a1'), 'read', true, NOW - 40 * 24 * 3600 * 1000);
  assert.deepEqual(S.prune(pending, file, NOW), {});
});
