/*
 * yoreader laptop page: the sync file logic (format in sync/README.md), without any
 * browser code so it can be tested with Node. Pending changes are the laptop's own
 * read/saved changes, kept until a write by the phone includes them:
 *   { "<feed>\n<guid>": { read: true, readAt: 1763…, saved: false, savedAt: 1763… } }
 * Pending sources are the ones added or removed on the laptop, kept until the phone
 * has done it:
 *   [ { url: "https://…", groups: ["Tech"], at: 1763… },     // add
 *     { url: "https://…", remove: true, at: 1763… } ]       // remove
 */
(function (root) {
  'use strict';

  var FORMAT = 'yoreader-sync';
  var VERSION = 1;
  /** Pending changes the phone never confirmed are dropped after this long. */
  var PENDING_MAX_AGE = 30 * 24 * 60 * 60 * 1000;
  var FIELDS = [['read', 'readAt'], ['saved', 'savedAt']];

  function SyncError(message) {
    this.name = 'SyncError';
    this.message = message;
  }
  SyncError.prototype = Object.create(Error.prototype);

  /** The parsed file, or a SyncError explaining what's wrong with it. */
  function parse(text) {
    if (!text || !text.trim()) {
      throw new SyncError('The sync file is still empty: open yoreader on the phone so it can fill it.');
    }
    var file;
    try {
      file = JSON.parse(text);
    } catch (e) {
      throw new SyncError("The sync file isn't valid JSON (" + e.message + ').');
    }
    if (!file || file.format !== FORMAT) throw new SyncError("This isn't a yoreader sync file.");
    if (file.version > VERSION) throw new SyncError('The sync file is from a newer yoreader: reload this page.');
    file.groups = Array.isArray(file.groups) ? file.groups : [];
    file.feeds = Array.isArray(file.feeds) ? file.feeds : [];
    file.articles = (Array.isArray(file.articles) ? file.articles : []).filter(function (a) {
      return a && a.feed && a.guid;
    });
    return file;
  }

  function key(article) {
    return article.feed + '\n' + article.guid;
  }

  function newer(pendingAt, fileAt) {
    return pendingAt != null && pendingAt > (fileAt == null ? 0 : fileAt);
  }

  /** [article] with the pending changes that are newer than the file's states. */
  function effective(article, pending) {
    var p = pending[key(article)];
    if (!p) return article;
    var out = Object.assign({}, article);
    FIELDS.forEach(function (f) {
      if (newer(p[f[1]], article[f[1]])) {
        out[f[0]] = p[f[0]];
        out[f[1]] = p[f[1]];
      }
    });
    return out;
  }

  /** The file's articles as the laptop should show them. */
  function view(file, pending) {
    return file.articles.map(function (a) {
      return effective(a, pending);
    });
  }

  /** Records a change made on the laptop ([field]: "read" or "saved"); returns new pending. */
  function change(pending, article, field, value, now) {
    var k = key(article);
    var entry = Object.assign({}, pending[k]);
    entry[field] = value;
    entry[field + 'At'] = now;
    var out = Object.assign({}, pending);
    out[k] = entry;
    return out;
  }

  /** Marks every unread one of [articles] read, as one change time. */
  function markAllRead(pending, articles, now) {
    return articles.reduce(function (out, a) {
      return a.read ? out : change(out, a, 'read', true, now);
    }, pending);
  }

  /**
   * Whether a source is listed in the side pane: only those with unread articles,
   * plus the one open ([selected]), one that fails to update ([feed.error]) and one
   * waiting to be removed ([removing]), so those stay in sight.
   */
  function sourceShown(feed, unread, selected, removing) {
    return unread > 0 || !!selected || !!feed.error || !!removing;
  }

  /**
   * Pending changes still needed: a write by the phone that has the same or a newer
   * state confirms a change; one for an article no longer in such a write is moot.
   */
  function prune(pending, file, now) {
    var byPhone = file.writtenBy === 'phone';
    var index = {};
    file.articles.forEach(function (a) {
      index[key(a)] = a;
    });
    var out = {};
    Object.keys(pending).forEach(function (k) {
      var p = pending[k];
      var a = index[k];
      if (!a && byPhone) return;
      var kept = {};
      var any = false;
      FIELDS.forEach(function (f) {
        var at = p[f[1]];
        if (at == null || now - at > PENDING_MAX_AGE) return;
        if (byPhone && a && !newer(at, a[f[1]])) return;
        kept[f[0]] = p[f[0]];
        kept[f[1]] = at;
        any = true;
      });
      if (any) out[k] = kept;
    });
    return out;
  }

  // --- Sources added on the laptop ---------------------------------------------------

  /**
   * A cleaned-up http(s) address for what was typed, or null if it isn't one; a
   * missing scheme means https. Same rules as the app (data/FeedUrl.kt).
   */
  function normalizeUrl(input) {
    var s = String(input || '').trim();
    if (!s || /\s/.test(s)) return null;
    if (s.indexOf('://') < 0) s = 'https://' + s;
    var u;
    try {
      u = new URL(s);
    } catch (e) {
      return null;
    }
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
    if (u.hostname.indexOf('.') < 0) return null;
    return s;
  }

  /** Same source: case doesn't matter, nor a trailing slash (as in the app). */
  function feedKey(url) {
    return String(url).toLowerCase().replace(/\/+$/, '');
  }

  function sameRequest(a, b) {
    return feedKey(a.url) === feedKey(b.url) && a.at === b.at;
  }

  /** The file's source for [url], if it has it. */
  function findFeed(file, url) {
    var k = feedKey(url);
    return file.feeds.find(function (f) { return feedKey(f.url) === k; }) || null;
  }

  /**
   * Whether the phone did [request]: it lists it as done, or (adding) the source is
   * there in those groups, or (removing) the source is gone.
   */
  function feedDone(file, request) {
    var done = request.remove ? file.removedFeeds : file.addedFeeds;
    if ((done || []).some(function (d) { return d && sameRequest(d, request); })) return true;
    var f = findFeed(file, request.url);
    if (request.remove) return !f;
    return !!f && request.groups.every(function (g) { return (f.groups || []).indexOf(g) >= 0; });
  }

  /** Adds a source to the pending ones (replacing an earlier request for it). */
  function addFeed(pendingFeeds, url, groups, now) {
    return withRequest(pendingFeeds, { url: url, groups: groups.slice(), at: now });
  }

  /** Asks to remove a source (replacing an earlier request for it). */
  function removeFeed(pendingFeeds, url, now) {
    return withRequest(pendingFeeds, { url: url, remove: true, at: now });
  }

  function withRequest(pendingFeeds, request) {
    var k = feedKey(request.url);
    return pendingFeeds.filter(function (r) { return feedKey(r.url) !== k; }).concat([request]);
  }

  /** The pending request for the source at [url], if any. */
  function requestFor(pendingFeeds, url) {
    var k = feedKey(url);
    return pendingFeeds.find(function (r) { return feedKey(r.url) === k; }) || null;
  }

  /** Pending requests the phone hasn't done yet (and not too old). */
  function pruneFeeds(pendingFeeds, file, now) {
    return pendingFeeds.filter(function (r) {
      return r && r.url && now - r.at <= PENDING_MAX_AGE && !feedDone(file, r);
    });
  }

  /** The file's requests, as pending requests (removals marked). */
  function fileRequests(file) {
    var adds = (file.addFeeds || []).filter(function (r) { return r && r.url && Array.isArray(r.groups); });
    var removes = (file.removeFeeds || []).filter(function (r) { return r && r.url; }).map(function (r) {
      return { url: r.url, remove: true, at: r.at };
    });
    return adds.concat(removes);
  }

  /** Requests to write: ours, plus any other laptop's still in the file. */
  function feedRequests(file, pendingFeeds) {
    var out = pendingFeeds.slice();
    fileRequests(file).forEach(function (r) {
      if (feedDone(file, r)) return;
      if (!out.some(function (o) { return feedKey(o.url) === feedKey(r.url); })) out.push(r);
    });
    return out;
  }

  /** Whether the file lacks some pending change (so the laptop should write it). */
  function needsWrite(file, pending, pendingFeeds) {
    var asked = fileRequests(file);
    var feedMissing = (pendingFeeds || []).some(function (r) {
      return !asked.some(function (a) { return !!a.remove === !!r.remove && sameRequest(a, r); });
    });
    return feedMissing || file.articles.some(function (a) {
      var p = pending[key(a)];
      return !!p && FIELDS.some(function (f) {
        return newer(p[f[1]], a[f[1]]);
      });
    });
  }

  /**
   * The file to write: [file] with the pending changes applied, written by the laptop.
   * "writtenBy"/"writtenAt" go first: the phone reads just the first bytes to see
   * whether anything changed.
   */
  function applyPending(file, pending, now, pendingFeeds) {
    var out = Object.assign({ writtenBy: 'web', writtenAt: now }, file);
    out.writtenBy = 'web';
    out.writtenAt = now;
    out.articles = view(file, pending);
    var requests = feedRequests(file, pendingFeeds || []);
    var adds = requests.filter(function (r) { return !r.remove; });
    var removes = requests.filter(function (r) { return r.remove; }).map(function (r) {
      return { url: r.url, at: r.at };
    });
    if (adds.length) out.addFeeds = adds;
    else delete out.addFeeds;
    if (removes.length) out.removeFeeds = removes;
    else delete out.removeFeeds;
    return out;
  }

  var api = {
    FORMAT: FORMAT,
    VERSION: VERSION,
    SyncError: SyncError,
    parse: parse,
    key: key,
    view: view,
    change: change,
    markAllRead: markAllRead,
    sourceShown: sourceShown,
    prune: prune,
    needsWrite: needsWrite,
    applyPending: applyPending,
    normalizeUrl: normalizeUrl,
    feedKey: feedKey,
    findFeed: findFeed,
    addFeed: addFeed,
    removeFeed: removeFeed,
    requestFor: requestFor,
    pruneFeeds: pruneFeeds,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.YoSync = api;
})(this);
