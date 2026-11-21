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

test('addresses are cleaned up like in the app', () => {
  assert.equal(S.normalizeUrl(' example.org/feed.xml '), 'https://example.org/feed.xml');
  assert.equal(S.normalizeUrl('http://example.org/rss'), 'http://example.org/rss');
  assert.equal(S.normalizeUrl('ftp://example.org/rss'), null);
  assert.equal(S.normalizeUrl('localhost/rss'), null);
  assert.equal(S.normalizeUrl('two words.org'), null);
  assert.equal(S.normalizeUrl(''), null);
  assert.equal(S.feedKey('https://Example.org/Feed/'), 'https://example.org/feed');
});

test('a source added on the laptop is asked for until the phone has it', () => {
  const file = sample();
  const pendingFeeds = S.addFeed([], 'https://new.example.net/feed', ['Tech'], NOW);
  assert.equal(S.needsWrite(file, {}, pendingFeeds), true);
  const written = S.applyPending(file, {}, NOW + 1, pendingFeeds);
  assert.deepEqual(written.addFeeds, [{ url: 'https://new.example.net/feed', groups: ['Tech'], at: NOW }]);
  // Read back: already asked, nothing more to write; still pending.
  assert.equal(S.needsWrite(written, {}, pendingFeeds), false);
  assert.equal(S.pruneFeeds(pendingFeeds, written, NOW + 2).length, 1);
  // The phone's next write, which crossed ours, lacks the request: ask again.
  assert.equal(S.needsWrite(file, {}, pendingFeeds), true);
});

test('the phone confirms a source by listing the request or the source', () => {
  const pendingFeeds = S.addFeed([], 'https://new.example.net/feed', ['Tech'], NOW);
  const acked = Object.assign(sample(), { addedFeeds: [{ url: 'https://new.example.net/feed', at: NOW }] });
  assert.deepEqual(S.pruneFeeds(pendingFeeds, acked, NOW + 5), []);
  const listed = sample();
  listed.feeds.push({ url: 'https://NEW.example.net/feed/', title: 'New', groups: ['Tech'] });
  assert.deepEqual(S.pruneFeeds(pendingFeeds, listed, NOW + 5), []);
  const otherGroup = sample();
  otherGroup.feeds.push({ url: 'https://new.example.net/feed', title: 'New', groups: [] });
  assert.equal(S.pruneFeeds(pendingFeeds, otherGroup, NOW + 5).length, 1, 'group not added yet');
  assert.deepEqual(S.pruneFeeds(pendingFeeds, sample(), NOW + 31 * 24 * 3600 * 1000), [], 'too old');
});

test("another laptop's request in the file is kept when writing", () => {
  const file = sample();
  file.writtenBy = 'web';
  file.addFeeds = [{ url: 'https://other.example.net/rss', groups: [], at: NOW - 5 }];
  const mine = S.addFeed([], 'https://new.example.net/feed', [], NOW);
  const written = S.applyPending(file, {}, NOW + 1, mine);
  assert.deepEqual(written.addFeeds.map((r) => r.url), ['https://new.example.net/feed', 'https://other.example.net/rss']);
  assert.equal(S.applyPending(sample(), {}, NOW + 1, []).addFeeds, undefined);
});

test('a source removed on the laptop is asked for until the phone drops it', () => {
  const file = sample();
  const url = 'https://tech.example.com/rss';
  const pendingFeeds = S.removeFeed([], url, NOW);
  assert.equal(S.needsWrite(file, {}, pendingFeeds), true);
  const written = S.applyPending(file, {}, NOW + 1, pendingFeeds);
  assert.deepEqual(written.removeFeeds, [{ url, at: NOW }]);
  assert.equal(written.addFeeds, undefined);
  assert.equal(S.needsWrite(written, {}, pendingFeeds), false);
  assert.equal(S.pruneFeeds(pendingFeeds, written, NOW + 2).length, 1, 'still there');
  // Done: the phone says so, or the source is gone.
  const acked = Object.assign(sample(), { removedFeeds: [{ url, at: NOW }] });
  assert.deepEqual(S.pruneFeeds(pendingFeeds, acked, NOW + 5), []);
  const gone = sample();
  gone.feeds = gone.feeds.filter((f) => f.url !== url);
  assert.deepEqual(S.pruneFeeds(pendingFeeds, gone, NOW + 5), []);
});

test('adding and removing the same source keeps only the last request', () => {
  const url = 'https://new.example.net/feed';
  let p = S.addFeed([], url, ['Tech'], NOW);
  p = S.removeFeed(p, 'https://NEW.example.net/feed/', NOW + 1);
  assert.equal(p.length, 1);
  assert.equal(S.requestFor(p, url).remove, true);
  p = S.addFeed(p, url, [], NOW + 2);
  assert.equal(p.length, 1);
  assert.equal(S.requestFor(p, url).remove, undefined);
  assert.equal(S.requestFor(p, 'https://other.example.net/'), null);
});

test('mark all read marks only the unread ones, at one time', () => {
  const file = sample();
  const unread = file.articles.filter((a) => !a.read);
  assert.ok(unread.length > 0);
  const pending = S.markAllRead({}, file.articles, NOW);
  assert.equal(Object.keys(pending).length, unread.length);
  for (const a of unread) assert.deepEqual(pending[S.key(a)], { read: true, readAt: NOW });
  assert.ok(S.view(file, pending).every((a) => a.read));
});

test('the side pane lists sources with unread articles, and the ones to keep in sight', () => {
  const feed = { url: 'https://a.example/feed', title: 'A' };
  assert.equal(S.sourceShown(feed, 3, false, false), true);
  assert.equal(S.sourceShown(feed, 0, false, false), false);
  assert.equal(S.sourceShown(feed, 0, true, false), true, 'the source open now');
  assert.equal(S.sourceShown({ ...feed, error: 'HTTP 404' }, 0, false, false), true, 'a failing source');
  assert.equal(S.sourceShown(feed, 0, false, true), true, 'a source being removed');
});

test('a Nostr public key is a source address, like on the phone', () => {
  const npub = 'npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg'; // NIP-19 example
  assert.equal(S.normalizeUrl(npub), 'nostr:' + npub);
  assert.equal(S.normalizeUrl('  nostr:' + npub.toUpperCase() + ' '), 'nostr:' + npub);
  assert.equal(S.normalizeUrl(npub.slice(0, -1) + 'q'), null, 'wrong checksum');
  assert.equal(S.normalizeUrl('nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5'), null, 'a private key');
  assert.equal(S.normalizeUrl('example.org/feed'), 'https://example.org/feed');
  const pending = S.addFeed([], S.normalizeUrl(npub), [], 1);
  assert.equal(pending[0].url, 'nostr:' + npub);
});
