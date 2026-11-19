# Changelog

Versions are `major.minor.patch`, set in `app/build.gradle.kts` (`appVersionName`).
The build number (Android `versionCode`) is the number of git commits, and every
build records its commit id; both show at the bottom of the side menu.

## 0.8.0 - 2026-11-19

- Fixed: cleaning up an article could close the app when the site refused the
  download (e.g. sites behind Cloudflare's browser check). Such sites now get a
  message saying to open the original page instead.
- Double tap an article in the reader to clean it up; double tap again to switch
  between the cleaned version and the feed's. Double-tapping on a link cleans
  too, instead of opening the link.
- The article text size from Options now also applies to article lists (titles,
  sources and previews), not only to the reader.
- Add and remove sources from the laptop page: type a feed or website address and
  choose groups, or open a source and press Remove source; the phone does it the
  next time it syncs. Sources that fail to refresh show a ⚠ on the laptop page.
- Text size changes in 5% steps instead of 10%.

## 0.7.0 - 2026-11-18

- New app icon: a cube of colored matchsticks with an orange RSS badge. The
  launcher still gives it its own shape (circle, squircle, rounded square).
- New Options screen in the side menu (below Manage sources):
  - Article text size, from 80% to 150%, with a live preview. Text grows
    together with the headline (which grows a bit less, so it doesn't take over
    the screen), in the reader and in cleaned pages.
  - Remove read articles after 30, 60, 90 (default), 180 days, 1 year, or never.
    Saved and unread articles are always kept, and removed ones don't come back
    on the next refresh.
  - About: version and build, link to the source code on GitHub, license.
  - Read-aloud languages: Czech, Dutch, English, Finnish, French, German,
    Italian, Polish, Portuguese (Brazil and Portugal), Romanian, Slovak, Spanish,
    Swedish and Turkish. Only languages with a voice installed on the phone are
    listed, alphabetically; turn on the ones you read (Spanish and English are on
    to start). Only the ones turned on are shown; the rest are under "More
    languages", which opens like a drop-down. A button opens Android's settings
    to add voices.
- Read aloud detects the article's language among the ones turned on. The
  language button in the "now playing" bar swaps between two languages, or opens
  a list when there are more; the choice is still remembered per source.
- Laptop page: read your articles in a browser on your computer (Chrome, Edge or
  Brave) at jorgonzalez.github.io/yoreader, with read and saved articles kept the
  same as on the phone. Set it up in Options → Laptop: the phone keeps a sync file
  in Google Drive, and the page uses that same file through Google Drive for
  desktop. No server or account involved; the newest change wins on both sides.

## 0.6.0 - 2026-11-17

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
