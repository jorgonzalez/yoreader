/*
 * yoreader laptop page: the sync file logic (format in sync/README.md), without any
 * browser code so it can be tested with Node. Pending changes are the laptop's own
 * read/saved changes, kept until a write by the phone includes them:
 *   { "<feed>\n<guid>": { read: true, readAt: 1763…, saved: false, savedAt: 1763… } }
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

  /** Whether the file lacks some pending change (so the laptop should write it). */
  function needsWrite(file, pending) {
    return file.articles.some(function (a) {
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
  function applyPending(file, pending, now) {
    var out = Object.assign({ writtenBy: 'web', writtenAt: now }, file);
    out.writtenBy = 'web';
    out.writtenAt = now;
    out.articles = view(file, pending);
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
    prune: prune,
    needsWrite: needsWrite,
    applyPending: applyPending,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.YoSync = api;
})(this);
