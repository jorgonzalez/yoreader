# Changelog

Versions are `major.minor.patch`, set in `app/build.gradle.kts` (`appVersionName`).
The build number (Android `versionCode`) is the number of git commits, and every
build records its commit id; both show at the bottom of the side menu.

## Unreleased

- Swiping an article out of a list no longer removes it instantly: the card springs
  back showing its new state (read, or saved) and fades out after 2 seconds. Undo
  in that time just cancels it.

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
