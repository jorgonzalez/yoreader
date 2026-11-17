# Changelog

Versions are `major.minor.patch`, set in `app/build.gradle.kts` (`appVersionName`).
The build number (Android `versionCode`) is the number of git commits, and every
build records its commit id; both show at the bottom of the side menu.

## Unreleased

- Read aloud follows the text: the paragraph being read is highlighted and the
  page scrolls smoothly to keep it in view (long paragraphs scroll along as
  they're read). Scrolling by hand stops following; the Follow button brings it
  back. Works in the reader and in cleaned pages opened from links.

## 0.5.0 - 2026-11-17

- Read aloud keeps going with the screen off or while using other apps. A media
  card in the notification panel and on the lock screen shows the article with
  Pause/Resume and Stop. Reading pauses for calls and other audio (and continues
  after short interruptions) and when headphones are unplugged. Android 13+ asks
  for permission to show notifications the first time you listen.

## 0.4.0 - 2026-11-17

- Read aloud no longer reads text that isn't on screen: some pages (e.g. CNN en
  Español) hide HTML inside their video players, and hidden elements are now
  skipped too. Articles cleaned before this are fixed without cleaning them again.

## 0.3.0 - 2026-11-16

- Swiping an article out of a list no longer removes it instantly: the card springs
  back showing its new state (read, or saved) and fades out after 2 seconds. Undo
  in that time just cancels it.
- Reader: a Share button in the top bar sends the article's title and link to any
  app. "Open original page" (in the app) moved into the ⋮ menu, next to "Open in
  browser".

## 0.2.0 - 2026-11-16

First version used on a phone.

- Sources and groups: add by feed or website address (feed autodiscovery), groups
  with many-to-many sources, import/export as OPML.
- Feeds: RSS 2.0, RSS 1.0 and Atom; background refresh about hourly; http allowed.
- Reading: Latest (newest 20 unread) as home; swipe left read / right save for
  later (20% minimum, undo); Saved; per-group, per-source and all lists (unread by
  default, with a count); accent-insensitive search.
- Layout: bottom bar (menu, saved, latest, search) and a side menu with expandable
  groups and their sources.
- Reader: dark-styled article view; links open inside the app; "Clean up" extracts
  just the article from its web page.
- Read aloud: Spanish/English per article (with an ES/EN switch remembered per
  source), speed 0.5x-3x, continues from the current sentence after speed changes
  and pauses, now-playing bar.
- App icon: Brandenburg Gate with an RSS badge.

## 0.1.0 - 2026-11-15

- Project set up: sources and groups screens and the Room data model; no feed
  fetching yet.
