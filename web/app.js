/*
 * yoreader laptop page. Reads the sync file the phone keeps (chosen through the
 * browser's file picker, e.g. in the Google Drive for desktop folder), shows the
 * articles, and writes read/saved changes back to it. Nothing leaves this computer:
 * the page has no server, and the file stays where it is.
 */
(function () {
  'use strict';

  var S = window.YoSync;
  var PENDING_KEY = 'yoreader.pending';
  var PENDING_FEEDS_KEY = 'yoreader.pendingFeeds';
  var VIEW_KEY = 'yoreader.view';
  var POLL_MS = 30 * 1000;
  var WRITE_DELAY_MS = 1500;

  var state = {
    handle: null,
    file: null,
    lastModified: null,
    pending: loadPending(),
    /** Sources added here that the phone hasn't added yet. */
    pendingFeeds: loadPendingFeeds(),
    view: loadView(),
    showRead: false,
    currentKey: null,
    /** Opened in this view: stay listed after being marked read, until the view changes. */
    sticky: {},
    error: null,
    writeTimer: null,
    busy: false,
  };

  var $ = function (id) { return document.getElementById(id); };

  // --- Storage of small things in the browser -------------------------------------

  function loadPending() {
    try { return JSON.parse(localStorage.getItem(PENDING_KEY)) || {}; } catch (e) { return {}; }
  }
  function savePending() {
    try { localStorage.setItem(PENDING_KEY, JSON.stringify(state.pending)); } catch (e) { /* private window */ }
  }
  function loadPendingFeeds() {
    try {
      var list = JSON.parse(localStorage.getItem(PENDING_FEEDS_KEY));
      return Array.isArray(list) ? list : [];
    } catch (e) {
      return [];
    }
  }
  function savePendingFeeds() {
    try { localStorage.setItem(PENDING_FEEDS_KEY, JSON.stringify(state.pendingFeeds)); } catch (e) { /* private window */ }
  }
  function loadView() {
    try { return JSON.parse(localStorage.getItem(VIEW_KEY)) || { kind: 'unread' }; } catch (e) { return { kind: 'unread' }; }
  }
  function saveView() {
    try { localStorage.setItem(VIEW_KEY, JSON.stringify(state.view)); } catch (e) { /* ignore */ }
  }

  /** The file handle is kept in IndexedDB (handles can't go in localStorage). */
  function idb(mode, fn) {
    return new Promise(function (resolve, reject) {
      var open = indexedDB.open('yoreader', 1);
      open.onupgradeneeded = function () { open.result.createObjectStore('kv'); };
      open.onerror = function () { reject(open.error); };
      open.onsuccess = function () {
        var tx = open.result.transaction('kv', mode);
        var req = fn(tx.objectStore('kv'));
        tx.oncomplete = function () { resolve(req && req.result); };
        tx.onerror = function () { reject(tx.error); };
      };
    });
  }
  function storeHandle(h) { return idb('readwrite', function (s) { return h ? s.put(h, 'file') : s.delete('file'); }); }
  function storedHandle() { return idb('readonly', function (s) { return s.get('file'); }).catch(function () { return null; }); }

  // --- Setup: choosing the file and getting access -------------------------------

  function showSetup(html) {
    $('app').hidden = true;
    $('setup').hidden = false;
    $('setup-body').innerHTML = html;
  }

  function unsupported() {
    var brave = navigator.brave !== undefined;
    showSetup(
      '<p>This browser doesn\'t let web pages open a file on your computer, which this page needs.</p>' +
        (brave
          ? '<p>In Brave it\'s off by default. To turn it on:</p><ol>' +
            '<li>Open <code>brave://flags/#file-system-access-api</code></li>' +
            '<li>Set <b>File System Access API</b> to <b>Enabled</b></li>' +
            '<li>Restart Brave and open this page again</li></ol>'
          : '<p>Use Chrome, Edge or Brave on a computer.</p>')
    );
  }

  function askForFile(message) {
    showSetup(
      (message ? '<p class="error">' + escapeHtml(message) + '</p>' : '') +
        '<p>Read your yoreader articles here and on the phone, with the same ones read and saved on both.</p>' +
        '<ol><li>On the phone: Options → Laptop → <b>Create sync file</b>, and save it in Google Drive.</li>' +
        '<li>On this computer, with Google Drive for desktop: choose that file below.</li></ol>' +
        '<p><button class="primary" id="choose">Choose sync file</button></p>' +
        '<p>The file stays where it is; nothing is uploaded anywhere.</p>'
    );
    $('choose').onclick = chooseFile;
  }

  function askToReconnect(handle) {
    showSetup(
      '<p>Allow this page to read and update <b>' + escapeHtml(handle.name) + '</b>, so what you read ' +
        'here is marked read on the phone too.</p>' +
        '<p><button class="primary" id="reconnect">Open ' + escapeHtml(handle.name) + '</button></p>' +
        '<p><button id="other">Choose another file</button></p>'
    );
    $('reconnect').onclick = function () {
      handle.requestPermission({ mode: 'readwrite' }).then(function (p) {
        if (p === 'granted') start(handle);
        else askForFile('Without permission to update the file, read articles can\'t be synced.');
      }).catch(function (e) { askForFile(e.message); });
    };
    $('other').onclick = chooseFile;
  }

  function chooseFile() {
    window.showOpenFilePicker({
      types: [{ description: 'yoreader sync file', accept: { 'application/json': ['.json'] } }],
      excludeAcceptAllOption: false,
    }).then(function (handles) {
      var handle = handles[0];
      state.lastModified = null;
      state.file = null;
      return storeHandle(handle).then(function () {
        return handle.queryPermission({ mode: 'readwrite' });
      }).then(function (p) {
        // Changing the file needs its own permission, asked from a click (browsers
        // don't allow asking right after the file picker closes).
        if (p === 'granted') start(handle);
        else askToReconnect(handle);
      });
    }).catch(function (e) {
      if (e && e.name !== 'AbortError') askForFile(e.message);
    });
  }

  function start(handle) {
    state.handle = handle;
    $('setup').hidden = true;
    $('app').hidden = false;
    reload(true);
  }

  // --- Reading and writing the file -----------------------------------------------

  function readFile() {
    return state.handle.getFile().then(function (f) {
      return f.text().then(function (text) { return { text: text, lastModified: f.lastModified }; });
    });
  }

  /** Reads the file again if it changed (or [force]), then shows it. */
  function reload(force) {
    if (!state.handle || state.busy) return Promise.resolve();
    return state.handle.getFile().then(function (f) {
      if (!force && state.file && f.lastModified === state.lastModified) return;
      return f.text().then(function (text) {
        var file = S.parse(text);
        state.file = file;
        state.lastModified = f.lastModified;
        state.error = null;
        state.pending = S.prune(state.pending, file, Date.now());
        state.pendingFeeds = S.pruneFeeds(state.pendingFeeds, file, Date.now());
        savePending();
        savePendingFeeds();
        if (S.needsWrite(file, state.pending, state.pendingFeeds)) scheduleWrite();
        render();
      });
    }).catch(fail);
  }

  function scheduleWrite() {
    clearTimeout(state.writeTimer);
    state.writeTimer = setTimeout(write, WRITE_DELAY_MS);
  }

  /** Writes the pending changes into the newest version of the file. */
  function write() {
    if (!state.handle) return Promise.resolve();
    if (state.busy) { scheduleWrite(); return Promise.resolve(); }
    state.busy = true;
    var now = Date.now();
    return readFile().then(function (read) {
      var file = S.parse(read.text);
      state.pending = S.prune(state.pending, file, now);
      state.pendingFeeds = S.pruneFeeds(state.pendingFeeds, file, now);
      savePending();
      savePendingFeeds();
      if (!S.needsWrite(file, state.pending, state.pendingFeeds)) {
        state.file = file;
        state.lastModified = read.lastModified;
        return;
      }
      var out = S.applyPending(file, state.pending, now, state.pendingFeeds);
      return state.handle.createWritable().then(function (w) {
        return w.write(JSON.stringify(out)).then(function () { return w.close(); });
      }).then(function () {
        state.file = out;
        return state.handle.getFile().then(function (f) { state.lastModified = f.lastModified; });
      });
    }).then(function () {
      state.busy = false;
      state.error = null;
      render();
    }, function (e) {
      state.busy = false;
      fail(e);
    });
  }

  function fail(e) {
    if (e && (e.name === 'NotAllowedError' || e.name === 'SecurityError')) {
      return askToReconnect(state.handle);
    }
    if (e && e.name === 'NotFoundError') {
      storeHandle(null);
      return askForFile('The sync file was moved or deleted. Choose it again.');
    }
    state.error = (e && e.message) || String(e);
    if (!state.file) return askForFile(state.error);
    render();
  }

  // --- Changes ---------------------------------------------------------------------

  function articles() {
    return state.file ? S.view(state.file, state.pending) : [];
  }

  function find(k) {
    return articles().find(function (a) { return S.key(a) === k; });
  }

  function setField(article, field, value) {
    if (!!article[field] === value) return;
    state.pending = S.change(state.pending, article, field, value, Date.now());
    if (state.handle) savePending(); // not in the demo
    scheduleWrite();
    render();
  }

  // --- What's shown ----------------------------------------------------------------

  function feedsByUrl() {
    var m = {};
    (state.file ? state.file.feeds : []).forEach(function (f) { m[f.url] = f; });
    return m;
  }

  function inView(a, view, feeds) {
    switch (view.kind) {
      case 'unread': return !a.read;
      case 'saved': return !!a.saved;
      case 'read': return !!a.read;
      case 'group':
        var f = feeds[a.feed];
        return !!f && (f.groups || []).indexOf(view.id) >= 0 && (state.showRead || !a.read);
      case 'feed': return a.feed === view.id && (state.showRead || !a.read);
    }
    return false;
  }

  function listed() {
    var feeds = feedsByUrl();
    var view = state.view;
    var list = articles().filter(function (a) {
      return inView(a, view, feeds) || state.sticky[S.key(a)];
    });
    var byDate = view.kind === 'read'
      ? function (a) { return a.readAt || a.published || 0; }
      : function (a) { return a.published || 0; };
    return list.sort(function (x, y) { return byDate(y) - byDate(x); });
  }

  function viewTitle(view) {
    switch (view.kind) {
      case 'unread': return 'Unread';
      case 'saved': return 'Saved';
      case 'read': return 'Recently read';
      case 'group': return view.id;
      case 'feed': var f = feedsByUrl()[view.id]; return f ? f.title : 'Source';
    }
    return '';
  }

  function setView(view) {
    state.view = view;
    state.sticky = {};
    saveView();
    render();
    $('list').scrollTop = 0;
  }

  function render() {
    if (!state.file) return;
    var all = articles();
    var feeds = feedsByUrl();
    // A view whose group or source is gone falls back to Unread.
    if ((state.view.kind === 'group' && state.file.groups.indexOf(state.view.id) < 0) ||
        (state.view.kind === 'feed' && !feeds[state.view.id])) {
      state.view = { kind: 'unread' };
    }
    renderNav(all, feeds);
    renderList();
    renderReader();
    renderStatus();
  }

  function navButton(label, count, view) {
    var b = document.createElement('button');
    var selected = state.view.kind === view.kind && state.view.id === view.id;
    b.className = 'nav-item' + (selected ? ' selected' : '') + (count ? '' : ' empty');
    b.innerHTML = '<span class="name"></span><span class="count"></span>';
    b.firstChild.textContent = label;
    b.lastChild.textContent = count ? String(count) : '';
    b.onclick = function () { setView(view); };
    return b;
  }

  function heading(text) {
    var h = document.createElement('h3');
    h.textContent = text;
    return h;
  }

  function renderNav(all, feeds) {
    var unreadByFeed = {};
    var unread = 0;
    var saved = 0;
    all.forEach(function (a) {
      if (!a.read) { unread++; unreadByFeed[a.feed] = (unreadByFeed[a.feed] || 0) + 1; }
      if (a.saved) saved++;
    });
    var box = $('nav-items');
    box.textContent = '';
    box.appendChild(navButton('Unread', unread, { kind: 'unread' }));
    box.appendChild(navButton('Saved', saved, { kind: 'saved' }));
    box.appendChild(navButton('Recently read', 0, { kind: 'read' }));
    if (state.file.groups.length) {
      box.appendChild(heading('Groups'));
      state.file.groups.forEach(function (g) {
        var n = 0;
        state.file.feeds.forEach(function (f) { if ((f.groups || []).indexOf(g) >= 0) n += unreadByFeed[f.url] || 0; });
        box.appendChild(navButton(g, n, { kind: 'group', id: g }));
      });
    }
    var waiting = state.pendingFeeds.filter(function (r) { return !r.remove && !S.findFeed(state.file, r.url); });
    if (state.file.feeds.length || waiting.length) {
      box.appendChild(heading('Sources'));
      state.file.feeds.forEach(function (f) {
        var b = navButton(f.title, unreadByFeed[f.url] || 0, { kind: 'feed', id: f.url });
        if ((S.requestFor(state.pendingFeeds, f.url) || {}).remove) {
          b.classList.add('removing');
          b.title = 'Waiting for the phone to remove it';
          b.lastChild.textContent = 'removing';
        } else if (f.error) {
          b.classList.add('failing');
          b.title = f.error;
        }
        box.appendChild(b);
      });
      waiting.forEach(function (r) {
        var item = document.createElement('div');
        item.className = 'nav-item pending';
        item.title = 'Waiting for the phone to add it';
        item.innerHTML = '<span class="name"></span><span class="count">waiting</span>';
        item.firstChild.textContent = r.url.replace(/^https?:\/\//, '');
        box.appendChild(item);
      });
    }
  }

  function renderList() {
    $('list-title').textContent = viewTitle(state.view);
    var filtered = state.view.kind === 'group' || state.view.kind === 'feed';
    $('show-read-label').hidden = !filtered;
    $('show-read').checked = state.showRead;
    var feeds = feedsByUrl();
    var source = state.view.kind === 'feed' ? feeds[state.view.id] : null;
    $('remove-source').hidden = !source;
    if (source) {
      var removing = (S.requestFor(state.pendingFeeds, source.url) || {}).remove;
      $('remove-source').disabled = !!removing;
      $('remove-source').textContent = removing ? 'Removal waiting for the phone' : 'Remove source';
    }
    var ol = $('list');
    ol.textContent = '';
    var items = listed();
    if (!items.length) {
      var li = document.createElement('li');
      li.className = 'none';
      li.textContent = state.view.kind === 'saved' ? 'Nothing saved for later.' :
        state.view.kind === 'read' ? 'Articles read in the last 7 days show here.' : 'All read.';
      ol.appendChild(li);
      return;
    }
    items.forEach(function (a) {
      var k = S.key(a);
      var li = document.createElement('li');
      li.className = (a.read ? 'read' : '') + (k === state.currentKey ? ' current' : '');
      li.dataset.key = k;
      var meta = document.createElement('div');
      meta.className = 'meta';
      var src = document.createElement('span');
      src.textContent = (feeds[a.feed] || {}).title || '';
      var date = document.createElement('span');
      date.textContent = a.published ? '· ' + relative(a.published) : '';
      meta.appendChild(src);
      meta.appendChild(date);
      if (a.saved) {
        var s = document.createElement('span');
        s.className = 'saved';
        s.textContent = '· Saved';
        meta.appendChild(s);
      }
      var title = document.createElement('div');
      title.className = 'title';
      title.textContent = a.title || '(untitled)';
      li.appendChild(meta);
      li.appendChild(title);
      if (a.excerpt) {
        var ex = document.createElement('div');
        ex.className = 'excerpt';
        ex.textContent = a.excerpt;
        li.appendChild(ex);
      }
      li.onclick = function () { open(k); };
      ol.appendChild(li);
    });
  }

  var shownKey = null;

  function renderReader() {
    var a = state.currentKey && find(state.currentKey);
    $('reader-bar').hidden = !a;
    $('reader-empty').hidden = !!a;
    $('app').classList.toggle('reading', !!a);
    if (!a) {
      if (shownKey !== null) { $('reader').srcdoc = ''; shownKey = null; }
      return;
    }
    $('toggle-read').textContent = a.read ? 'Mark unread' : 'Mark read';
    $('toggle-saved').textContent = a.saved ? 'Saved' : 'Save';
    $('toggle-saved').classList.toggle('on', !!a.saved);
    $('open-original').href = a.link || '#';
    // Only reload the page when the article changes (not on every status change).
    if (shownKey !== state.currentKey) {
      shownKey = state.currentKey;
      $('reader').srcdoc = articlePage(a, (feedsByUrl()[a.feed] || {}).title || '');
    }
  }

  function renderStatus() {
    var f = state.file;
    var who = f.writtenBy === 'phone' ? 'the phone' : 'this page';
    var html = 'Updated by ' + who + (f.writtenAt ? ' ' + relative(f.writtenAt) : '');
    var waiting = Object.keys(state.pending).length + state.pendingFeeds.length;
    if (waiting) html += '<br>' + waiting + ' change' + (waiting > 1 ? 's' : '') + ' waiting for the phone';
    if (state.error) html += '<br><span class="error"></span>';
    html += '<br><button id="change-file">Change sync file</button>';
    $('status').innerHTML = html;
    if (state.error) $('status').querySelector('.error').textContent = state.error;
    $('change-file').onclick = chooseFile;
  }

  /**
   * The article as a page for the sandboxed frame (no scripts run there), styled like
   * the app's reader (app/.../ui/reader/ArticleHtml.kt).
   */
  function articlePage(a, feedTitle) {
    var meta = [feedTitle, a.published ? new Date(a.published).toLocaleString() : null]
      .filter(Boolean).map(escapeHtml).join(' · ');
    var body = a.html ||
      '<p class="empty">The text of read articles stays on the phone. Use “Open original” to read it on its site.</p>';
    return '<!DOCTYPE html><html><head><meta charset="utf-8"><base target="_blank">' +
      '<style>' + READER_CSS + '</style></head><body><main>' +
      '<p class="meta">' + meta + '</p><h1>' + escapeHtml(a.title || '') + '</h1>' +
      '<article>' + body + '</article></main></body></html>';
  }

  var READER_CSS = [
    'html { color-scheme: dark; }',
    'body { margin: 0; padding: 24px 32px 64px; background: #0E0F12; color: #E6E7EA;',
    '  font: 18px/1.65 Georgia, "Iowan Old Style", serif; overflow-wrap: break-word; }',
    'main { max-width: 720px; margin: 0 auto; }',
    'article * { color: inherit !important; background-color: transparent !important; }',
    'a, article a { color: #7CB7FF !important; }',
    '.meta { color: #9AA0AB; font: 14px system-ui, sans-serif; margin: 0 0 6px; }',
    'h1 { font: 700 30px/1.25 system-ui, sans-serif; margin: 0 0 24px; }',
    'h2, h3, h4 { font-family: system-ui, sans-serif; line-height: 1.3; }',
    'img, video, iframe, svg { max-width: 100%; height: auto; border-radius: 6px; }',
    'figure { margin: 16px 0; }',
    'figcaption { color: #9AA0AB !important; font-size: 14px; }',
    'blockquote { margin: 16px 0; padding-left: 14px; border-left: 3px solid #3A3F4A; color: #9AA0AB !important; }',
    'pre, code { font-family: ui-monospace, monospace; font-size: 15px; }',
    'pre { overflow-x: auto; padding: 12px; background-color: #1F222A !important; border-radius: 6px; }',
    'table { display: block; overflow-x: auto; border-collapse: collapse; }',
    'td, th { border: 1px solid #3A3F4A; padding: 4px 8px; }',
    'hr { border: none; border-top: 1px solid #3A3F4A; }',
    '.empty { color: #9AA0AB; font-style: italic; }',
  ].join('\n');

  // --- Actions -----------------------------------------------------------------------

  function open(k) {
    state.currentKey = k;
    state.sticky[k] = true;
    var a = find(k);
    if (a && !a.read) setField(a, 'read', true);
    else render();
    var li = $('list').querySelector('li.current');
    if (li) li.scrollIntoView({ block: 'nearest' });
  }

  function step(delta) {
    var items = listed();
    if (!items.length) return;
    var i = items.findIndex(function (a) { return S.key(a) === state.currentKey; });
    var next = i < 0 ? 0 : Math.min(items.length - 1, Math.max(0, i + delta));
    open(S.key(items[next]));
  }

  function current() {
    return state.currentKey && find(state.currentKey);
  }

  $('toggle-read').onclick = function () { var a = current(); if (a) setField(a, 'read', !a.read); };
  $('toggle-saved').onclick = function () { var a = current(); if (a) setField(a, 'saved', !a.saved); };
  $('back').onclick = function () { state.currentKey = null; render(); };
  $('show-read').onchange = function (e) { state.showRead = e.target.checked; render(); };

  // --- Adding a source ---------------------------------------------------------------

  function openAddSource() {
    var box = $('add-source-groups');
    box.textContent = '';
    state.file.groups.forEach(function (g) {
      var label = document.createElement('label');
      var input = document.createElement('input');
      input.type = 'checkbox';
      input.value = g;
      // Viewing a group: that's probably where it goes.
      input.checked = state.view.kind === 'group' && state.view.id === g;
      label.appendChild(input);
      label.appendChild(document.createTextNode(' ' + g));
      box.appendChild(label);
    });
    $('add-source-message').hidden = true;
    $('add-source-open').hidden = true;
    $('add-source-form').hidden = false;
    $('add-source-url').focus();
  }

  function closeAddSource() {
    $('add-source-form').hidden = true;
    $('add-source-open').hidden = false;
    $('add-source-url').value = '';
  }

  function addSourceMessage(text) {
    $('add-source-message').textContent = text;
    $('add-source-message').hidden = false;
  }

  $('remove-source').onclick = function () {
    var f = state.view.kind === 'feed' && feedsByUrl()[state.view.id];
    if (!f) return;
    if (!confirm('Remove “' + f.title + '”? Its articles go too, also on the phone (when it next syncs).')) return;
    state.pendingFeeds = S.removeFeed(state.pendingFeeds, f.url, Date.now());
    if (state.handle) savePendingFeeds(); // not in the demo
    scheduleWrite();
    render();
  };

  $('add-source-open').onclick = openAddSource;
  $('add-source-cancel').onclick = closeAddSource;
  $('add-source-form').onkeydown = function (e) { if (e.key === 'Escape') closeAddSource(); };
  $('add-source-form').onsubmit = function (e) {
    e.preventDefault();
    var url = S.normalizeUrl($('add-source-url').value);
    if (!url) return addSourceMessage("That doesn't look like a web address.");
    var groups = Array.prototype.filter.call($('add-source-groups').querySelectorAll('input'), function (i) {
      return i.checked;
    }).map(function (i) { return i.value; });
    var existing = S.findFeed(state.file, url);
    if (existing && (S.requestFor(state.pendingFeeds, existing.url) || {}).remove) {
      return addSourceMessage('“' + existing.title + '” is waiting for the phone to remove it; add it again after that.');
    }
    if (existing && groups.every(function (g) { return (existing.groups || []).indexOf(g) >= 0; })) {
      return addSourceMessage('“' + existing.title + '” is already one of your sources.');
    }
    state.pendingFeeds = S.addFeed(state.pendingFeeds, existing ? existing.url : url, groups, Date.now());
    if (state.handle) savePendingFeeds(); // not in the demo
    closeAddSource();
    scheduleWrite();
    render();
  };

  document.addEventListener('keydown', function (e) {
    if (e.ctrlKey || e.metaKey || e.altKey || !state.file || $('app').hidden) return;
    if (e.target && /INPUT|TEXTAREA|SELECT/.test(e.target.tagName)) return;
    var a = current();
    switch (e.key) {
      case 'j': case 'ArrowDown': step(1); break;
      case 'k': case 'ArrowUp': step(-1); break;
      case 'm': if (a) setField(a, 'read', !a.read); break;
      case 's': if (a) setField(a, 'saved', !a.saved); break;
      case 'o': if (a && a.link) window.open(a.link, '_blank', 'noopener'); break;
      case 'Escape': state.currentKey = null; render(); break;
      default: return;
    }
    e.preventDefault();
  });

  // Look for changes from the phone now and then, and when coming back to the page.
  setInterval(function () { if (!document.hidden) reload(false); }, POLL_MS);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) reload(false); });
  window.addEventListener('focus', function () { reload(false); });
  // Leaving with changes not yet written: write them now.
  window.addEventListener('pagehide', function () { if (state.writeTimer) write(); });

  // --- Helpers -------------------------------------------------------------------------

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function relative(t) {
    var mins = Math.round((Date.now() - t) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return mins + ' min ago';
    var hours = Math.round(mins / 60);
    if (hours < 24) return hours + ' h ago';
    var days = Math.round(hours / 24);
    if (days < 7) return days + ' d ago';
    return new Date(t).toLocaleDateString();
  }

  // --- Start -------------------------------------------------------------------------

  /**
   * For trying the page without a file, served over http: index.html#demo shows the
   * sample, #demo=<url> another file. Changes aren't written anywhere.
   */
  function demo(url) {
    return fetch(url || '../sync/sample.json').then(function (r) { return r.text(); }).then(function (text) {
      state.file = S.parse(text);
      state.handle = null;
      $('setup').hidden = true;
      $('app').hidden = false;
      render();
    });
  }

  var demoMatch = /^#demo(?:=(.+))?$/.exec(location.hash);
  if (demoMatch) { demo(demoMatch[1] && decodeURIComponent(demoMatch[1])); return; }
  if (!window.showOpenFilePicker) { unsupported(); return; }
  storedHandle().then(function (handle) {
    if (!handle) return askForFile();
    return handle.queryPermission({ mode: 'readwrite' }).then(function (p) {
      if (p === 'granted') start(handle);
      else askToReconnect(handle);
    });
  }).catch(function () { askForFile(); });
})();
