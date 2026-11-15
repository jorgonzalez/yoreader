# yoreader

An Android RSS reader with a dark UI, user-managed source groups, text-to-speech
reading (Spanish and English), and a one-tap "clean up" that turns a cluttered web
page into readable article text.

## Features

- **Dark only.** One dark Material 3 scheme, no light theme.
- **Groups.** Sources can be added to any number of groups; groups can be created,
  renamed, reordered and deleted.
- **Read aloud.** Android `TextToSpeech`, Spanish and English, language detected per
  article. Playback runs in a foreground media service so it continues with the
  screen off.
- **Clean up.** Main-content extraction for any page you open, in the spirit of
  trafilatura, using Readability4J.

## Stack

Kotlin, Jetpack Compose (Material 3), Room (KSP), Coroutines/Flow, OkHttp,
WorkManager, Media3/MediaSession (planned).

## Current state

Sources, groups, feed fetching, the article list and a basic reader work.

- Gradle project (Kotlin DSL, version catalog), minSdk 26, targetSdk 35
- Dark theme
- Room data model: `Feed`, `FeedGroup`, `FeedGroupCrossRef` (many-to-many),
  `Article`, plus DAOs and `AppDatabase` (schema v2, with a migration from v1)
- Layout: an always-visible bottom bar (menu, saved, latest, search) and a side
  drawer opened from the menu icon (All articles, groups with unread counts, new
  group, manage sources, and an "Add source" button at the bottom).
- Screens (Navigation Compose):
  - **Latest** (start screen): the newest 20 unread articles that aren't saved,
    from all sources. Pull to refresh.
  - **Swipes** on any article list: left toggles read, right toggles "saved for
    later", with Undo. On Latest both remove the article from the list.
  - **Saved for later**: saved articles (stored in `Article.isStarred`).
  - **Search**: sources (title, URL) and articles (title, summary, text), with
    saved matches listed separately. Ignores case and accents ("camion" finds
    "camión"), using `Article.searchText`, a folded plain-text copy (schema v3).
  - **Articles** (all, one group, or one source): newest first, All/Unread filter,
    pull to refresh, mark all as read. In a group there is an "Add source" button
    (a URL that is already a source elsewhere is linked to the group) and an
    "Edit group" button
  - **Reader**: the feed's content in a WebView styled for the dark theme (JavaScript
    off, links open in the browser); opening marks it read. Top bar: read aloud,
    clean up, save for later, open original; menu: mark unread, share, switch
    between cleaned and feed version, clean up again.
- **Clean up** (`clean/ArticleCleaner`): downloads the article's page and keeps only
  the article with Readability4J + jsoup. Stored as `cleanedHtml` (shown in the
  reader) and `cleanedText` (paragraphs, used for listening and search). Some sites
  refuse non-browser requests (e.g. Slashdot answers HTTP 403) or show a paywall;
  the reader then says why it couldn't clean.
- **Read aloud** (`tts/`): Android TextToSpeech, one app-wide `ArticleSpeaker`.
  Reads the version on screen (cleaned if shown), title first, in chunks the engine
  accepts. Language per article from its text (Spanish/English function words),
  falling back to the feed's language. A "now playing" bar above the bottom bar
  shows progress with pause/resume and stop on every screen. Not yet: a foreground
  media service (so reading may stop if Android kills the app in the background),
  lock-screen controls, speed setting.
  - **Edit group**: rename, delete, add a source by URL, remove sources
  - **Sources**: add by URL (optionally into groups); tap a source for its articles;
    its menu changes groups, refreshes, or deletes. Each source shows when it was
    last updated, unread/total articles, or the last error.
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
- Unit tests for the parser, dates and discovery: `./gradlew :app:testDebugUnitTest`

## Open the project

1. Open this folder in Android Studio (Ladybug or newer).
2. Gradle 8.9 needs JDK 17 or 21 (not 25). In Android Studio set
   Settings > Build Tools > Gradle > Gradle JDK; on the command line set `JAVA_HOME`.
3. Sync and run the `app` configuration, or `./gradlew :app:assembleDebug`.

## Roadmap

1. **Sources and groups UI** - done, except reordering groups.
2. **Feed fetching** - done.
3. **Article list and reader** - done. Lists show the newest 1000 articles; there is
   no clean-up of old articles yet.
4. **Clean up** - done (per article, from the reader).
5. **TTS** - basic read-aloud done. Still to do: foreground media service with
   lock-screen/notification controls, speed control, sentence highlighting.
6. **Polish** - OPML import/export, offline reading, article search.

## Layout

```
app/src/main/java/com/yoreader/app/
  YoReaderApp.kt        Application, holds the database
  MainActivity.kt       Edge-to-edge dark activity
  data/                 Room entities, DAOs, database
  fetch/                Feed download, parsing, background refresh
  ui/                   Compose screens
  clean/                Clean up (main-content extraction)
  tts/                  Read aloud
  ui/shell/             Bottom bar, side drawer, now-playing bar
  ui/articles/          Article lists and swipeable rows
  ui/search/            Search
  ui/reader/            Article reader
  ui/theme/             Dark color scheme
```
