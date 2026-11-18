# yoreader

An Android RSS reader with a dark UI, user-managed source groups, text-to-speech
reading (15 languages, as the phone's voices allow), and a one-tap "clean up" that
turns a cluttered web page into readable article text.

## Download

**[yoreader-0.7.0.apk](https://github.com/jorgonzalez/yoreader/raw/main/releases/yoreader-0.7.0.apk)**
(Android 8.0 or newer). Open it on the phone and allow installing from that source
when asked. What's in each version: [CHANGELOG.md](CHANGELOG.md). All versions are
in [releases/](releases/).

SHA-256 of `yoreader-0.7.0.apk`:

```
4fd00792267e6330471ccbd64141dea918cbe75e4151687da0f07ab89cb3571a
```

To check a download, compare it with the output of `shasum -a 256 yoreader-0.7.0.apk`
(macOS), `sha256sum yoreader-0.7.0.apk` (Linux) or
`Get-FileHash yoreader-0.7.0.apk` (Windows PowerShell).

## Features

- **Dark only.** One dark Material 3 scheme, no light theme.
- **Groups.** Sources can be in any number of groups; groups can be created,
  renamed and deleted.
- **Read aloud.** Android `TextToSpeech` in Czech, Dutch, English, Finnish, French,
  German, Italian, Polish, Portuguese (Brazil and Portugal), Romanian, Slovak,
  Spanish, Swedish and Turkish (those with a voice on the phone, chosen in
  Options), language detected per article, with a language switch and speed control; the paragraph being read is
  highlighted and kept in view; keeps reading with the screen off, with controls
  in the notification panel and on the lock screen.
- **Clean up.** Main-content extraction for any article or page you open, in the
  spirit of trafilatura, using Readability4J.
- **Laptop page.** Read the same articles in a browser on your computer
  (Chrome, Edge or Brave), with read and saved kept the same on both, through a
  sync file in Google Drive. No server and no account: see [Laptop page](#laptop-page).

## Stack

Kotlin, Jetpack Compose (Material 3), Navigation Compose, Room (KSP),
Coroutines/Flow, OkHttp, WorkManager, Readability4J + jsoup, androidx.media
(media session).

## Current state

Everything below works and is in version 0.7.0 unless marked otherwise.

- Gradle project (Kotlin DSL, version catalog), minSdk 26, targetSdk 35
- Room data model: `Feed`, `FeedGroup`, `FeedGroupCrossRef` (many-to-many),
  `Article`, plus DAOs and `AppDatabase` (schema v6, with migrations from v1)
- **Layout:** an always-visible bottom bar (menu, saved, latest, search; each closes
  the side menu) and a side menu opened from the menu icon: All articles; groups
  with unread counts, each expandable to "All in <group>" and its sources with
  their unread counts; new group; manage sources; an "Add source" button at the
  bottom, with the version and build below it.
- **Latest** (start screen): the newest 20 unread articles that aren't saved, from
  all sources. Pull to refresh.
- **Swipes** on any article list: left toggles read, right toggles "saved for
  later" (a second right swipe un-saves). The card must move at least 20% of its
  width. It springs back showing its new state, and if it no longer belongs in the
  list it stays for 2 seconds, then fades out. Undo in the
  snackbar puts it back.
- **Saved for later**: saved articles (stored in `Article.isStarred`).
- **Articles** (all, one group, or one source): newest first, unread only by
  default (an "All" chip shows read ones too), the number of listed articles in a
  circle next to the chips, pull to refresh, mark all as read. A group also has
  "Add source" (a URL that is already a source elsewhere is just linked to the
  group) and "Edit group" (rename, delete, add or remove sources).
- **Search**: sources (title, URL) and articles (title, summary, text), with saved
  matches listed separately. Ignores case and accents ("camion" finds "camión"),
  using `Article.searchText`, a folded plain-text copy.
- **Reader**: the article in a WebView styled for the dark theme (JavaScript off);
  opening marks it read, and a headline repeated at the top of the content is
  dropped. Top bar: read aloud, clean up, save for later, share (title and link, to any
  app: WhatsApp, Signal, email...); menu: mark unread, open original page (in
  the app), open in browser, switch between cleaned and feed version, clean
  up again.
- **Links open in the app** (`ui/reader/WebPageScreen`): "Open original page" and links in
  articles open an in-app page (a small browser: JavaScript on, links followed,
  Back goes back, plain http allowed). Its top bar has Read aloud (cleans the page
  first if needed), Clean up (toggles cleaned/original), Share and Open in browser.
  Cleaned pages opened this way are kept in memory only, not stored.
- **Clean up** (`clean/ArticleCleaner`): downloads the article's page and keeps only
  the article with Readability4J + jsoup. Stored as `cleanedHtml` (shown in the
  reader) and `cleanedText` (paragraphs, used for listening and search). Some sites
  refuse non-browser requests (e.g. Slashdot answers HTTP 403) or show a paywall;
  the reader then says why it couldn't clean.
- **Read aloud** (`tts/`): Android TextToSpeech, one app-wide `ArticleSpeaker`.
  Reads the version on screen (cleaned if shown), title first, in chunks the engine
  accepts. **Languages**: Options lists the 15 supported languages that have a
  voice installed on the phone (`tts/TtsLanguages`, checked against the engine's
  installed voices), alphabetically, each with a switch; Spanish and English are on
  by default. Language per article from its text, among the ones turned on
  (`tts/LanguageGuess`: common function words of each language, plus letters only
  one of the languages turned on uses, e.g. ñ when it's just Spanish and English;
  Portuguese picks Brazil or Portugal from spellings like você/fato vs
  facto/equipa), falling back to the feed's declared language (often wrong: many
  Spanish blogs declare en-US). A "now playing" bar above the bottom bar shows
  progress, a language button (a tap swaps two languages, a list with more;
  remembered per source: feed, or website for in-app pages), speed − / + (0.5× to 3×,
  remembered), pause/resume and stop on every screen. Speed changes and
  pause/resume continue from the start of the sentence being read (or the current
  word in a very long sentence), using the engine's word-position callbacks.
  **Following the text**: in the reader (and in cleaned pages opened from links)
  the paragraph being read is highlighted and kept in view, scrolling smoothly;
  long paragraphs scroll along as they're read. Scrolling by hand stops
  following; a Follow button brings it back. The page and the text read aloud
  come from one pass over the article HTML (`Html.readable`), which numbers the
  paragraphs on screen and strips the article's own scripts, so the reader can
  run its small follow script safely.
  **With the screen off** (`tts/ReadAloudService`): while reading, a foreground
  media service keeps the app running and shows a media card in the notification
  panel and on the lock screen (title, Pause/Resume, Stop). Reading pauses for
  calls and other audio (continuing after short interruptions) and when headphones
  are unplugged; swiping yoreader away from recent apps stops it. Android 13+ asks
  for notification permission the first time you listen. Not yet: headset/earbud
  buttons (Android sends them to the app that plays the audio, which is the speech
  engine).
- **Sources** (Manage sources): add by URL (optionally into groups); tap a source
  for its articles; its menu changes groups, refreshes, or deletes. Each source
  shows when it was last updated, unread/total articles, or the last error. The
  top bar's menu exports all sources and groups to an OPML file, or imports one
  (from any reader): groups become OPML folders; import only adds (new feeds are
  fetched, missing groups created, existing feeds just joined to the file's
  groups).
- **Feed fetching** (`fetch/`):
  - `FeedParser`: RSS 2.0, RSS 1.0 (RDF) and Atom, on Android's XmlPullParser.
    Handles `content:encoded`, Atom xhtml content, relative links, undeclared
    HTML entities, and untitled items.
  - `FeedDates`: lenient RFC 822 / ISO 8601 parsing, including Spanish month names.
  - `FeedFetcher`: OkHttp with conditional GET (ETag / Last-Modified), a 10 MB cap,
    and feed autodiscovery, so a site URL like `xkcd.com` finds `xkcd.com/atom.xml`.
  - `FeedRefresher`: stores new articles (deduped by `(feedId, guid)` so read state
    survives), replaces the placeholder title with the feed's own, records errors.
    Fetches up to 4 feeds at once.
  - `RefreshWorker` / `RefreshScheduler`: WorkManager. Every feed is refreshed
    about hourly when online and the battery isn't low; a newly added source is
    fetched immediately; manual refresh from the top bar.
- **Options** (side menu, below Manage sources): article text size (80% to
  150%, with a live preview; body text grows fully, the headline half as much,
  and changing it doesn't reload the page), read-aloud languages (those turned on
  are listed; the others with a voice on the phone are under "More languages", a
  drop-down), the laptop sync file, when read articles are removed
  (30/60/90/180 days, 1 year or never; 90 by default; saved and unread articles
  are always kept), and About (version, GitHub link, license). Removed articles
  are remembered per feed (`removed_articles`), so a refresh doesn't bring them
  back while the feed still lists them. Clean-up runs at start-up, when the
  setting changes, and after each background refresh of all feeds.
- **Laptop sync** (`sync/`, Options → Laptop): the phone keeps a sync file chosen
  with Android's file picker (e.g. in Google Drive) with its feeds, groups, unread
  and saved articles (with their text) and the ones read in the last 7 days, and
  takes the read/saved changes made on the laptop page. Each change carries its
  time (`readChangedAt`/`starredChangedAt`), and the newest wins. Syncs when the app
  is opened, when it's left (as background work, so it can finish), after each
  background refresh, and with "Sync now". A sync with nothing new costs a few
  milliseconds: the phone only reads the first bytes of the file and a short summary
  of the database.
- **Laptop page** (`web/`, plain HTML/CSS/JS, no build step): unread, saved,
  recently read, groups and sources with unread counts; the article in a sandboxed
  frame styled like the reader; mark read/unread and save; keys j/k, m, s, o. Format
  of the file shared with the phone: [`sync/README.md`](sync/README.md).
- **Unit tests** (64): feed parsing, dates, HTML helpers and feed discovery, search
  folding, OPML, clean up, speech text and speed, language detection in all 14
  languages, the reader's HTML
  (paragraph numbering, script stripping, text size), the Options values
  (text sizes, clean-up period) and the sync file. Run with
  `./gradlew :app:testDebugUnitTest`. The laptop page's sync logic has its own
  tests (7), on the same sample file: `node --test web/test/*.test.js`.

## Laptop page

Read on a computer what the phone downloads, with the same articles read and saved
on both. The phone does the downloading (as always); the computer reads a file the
phone keeps up to date, so there's no server and nothing to sign in to.

1. **Phone:** Options → Laptop → **Create sync file**, and save it in Google Drive
   (any storage app in the file picker works, e.g. Dropbox).
2. **Computer:** install Google Drive for desktop, so the file is also in a folder on
   the computer.
3. Open [jorgonzalez.github.io/yoreader](https://jorgonzalez.github.io/yoreader/) in
   Chrome, Edge or Brave and choose that file. The page runs in the browser only: it
   reads and writes the file on your computer, and your articles never go to GitHub.
   It can also be opened from a copy of `web/` served locally.

**Brave** turns off the feature the page needs (the File System Access API) by
default: open `brave://flags/#file-system-access-api`, set it to Enabled, and restart.

What you read on the computer reaches the phone when yoreader is opened there (or
at its next background refresh); what you read on the phone reaches the computer
when you leave the app, plus however long Google Drive takes to copy the file. Read
articles are in the file for 7 days without their text (use "Open original").

## Open the project

1. Open this folder in Android Studio (Ladybug or newer).
2. Gradle 8.9 needs JDK 17 or 21 (not 25). In Android Studio set
   Settings > Build Tools > Gradle > Gradle JDK; on the command line set `JAVA_HOME`.
3. Sync and run the `app` configuration, or `./gradlew :app:assembleDebug`.

## Versions

`appVersionName` in `app/build.gradle.kts` is the version (raise it and add a
`CHANGELOG.md` entry when something notable is ready). The build number is the git
commit count and each build embeds its commit id (`-dirty` if built with uncommitted
changes); the side menu shows all three, e.g. `yoreader 0.2.0 · build 12 · b6a79a3`.

## Publishing a release

Release APKs are signed with the yoreader key, kept outside the repository. Its
properties file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) is named in
the git-ignored `local.properties`:

```
yoreader.signing.properties=/path/to/apk-keys/yoreader-release.properties
```

Keep the key and its password backed up: an app signed with a different key can't
update an installed yoreader. To publish:

1. Raise `appVersionName` in `app/build.gradle.kts` and add a `CHANGELOG.md` entry.
2. Commit, so the build carries a clean commit id.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/yoreader-<version>.apk`.
4. Copy it to `releases/`; update the Download link and the SHA-256 above
   (`shasum -a 256 releases/yoreader-<version>.apk`); commit and push.
5. Tag that commit and push the tag:
   `git tag -a v<version> -m "yoreader <version>" && git push origin v<version>`.

## Roadmap

1. **Sources and groups UI** - done, except reordering groups.
2. **Feed fetching** - done.
3. **Article list and reader** - done. Lists show the newest 1000 articles; read
   articles are removed after 90 days (configurable in Options).
4. **Clean up** - done (per article, from the reader).
5. **TTS** - read-aloud with speed control, screen-off playback and
   notification/lock-screen controls done. Still to do: speed changes with no
   repeat at all and headset buttons (both by playing pre-rendered audio in the
   app). Following the text on screen is done, by paragraph; by sentence or word
   is possible later.
   Laptop page (read and save on a computer, synced through a file in Google Drive)
   done.
6. **Polish** - OPML import/export, article search and Options (text size,
   clean-up of old read articles, read-aloud languages) done; offline reading to do.
7. **Sharing** (idea, not decided) - share articles with contacts inside the app,
   with a comment, over Nostr, with no server of our own.

## Layout

```
app/src/main/java/com/yoreader/app/
  YoReaderApp.kt        Application, holds the database
  MainActivity.kt       Edge-to-edge dark activity
  data/                 Room entities, DAOs, database
  fetch/                Feed download, parsing, background refresh
  ui/                   Compose screens
  clean/                Clean up (main-content extraction)
  sync/                 Laptop sync file (read/write, merge, background work)
  settings/             Options stored on the phone (SharedPreferences)
  tts/                  Read aloud
  ui/shell/             Bottom bar, side drawer, now-playing bar
  ui/articles/          Article lists and swipeable rows
  ui/search/            Search
  ui/reader/            Article reader
  ui/options/           Options screen
  ui/theme/             Dark color scheme
sync/                   Sync file format (README) and the sample both test suites read
web/                    Laptop page (index.html, app.js, sync.js, style.css; tests in web/test)
```
