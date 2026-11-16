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
  - **Articles** (all, one group, or one source): newest first, unread only by
    default (an "All" chip shows read ones too),
    pull to refresh, mark all as read. In a group there is an "Add source" button
    (a URL that is already a source elsewhere is linked to the group) and an
    "Edit group" button
  - **Reader**: the feed's content in a WebView styled for the dark theme (JavaScript
    off, links open in the browser); opening marks it read. Top bar: read aloud,
    clean up, save for later, open original; menu: mark unread, share, switch
    between cleaned and feed version, clean up again.
- **Links open in the app** (`ui/reader/WebPageScreen`): "Open original" and links in
  articles open an in-app page (a small browser: JavaScript on, links followed,
  Back goes back). Its top bar has Read aloud (cleans the page first if needed),
  Clean up (toggles cleaned/original), Share and Open in browser. Cleaned pages
  opened this way are kept in memory only, not stored.
- **Clean up** (`clean/ArticleCleaner`): downloads the article's page and keeps only
  the article with Readability4J + jsoup. Stored as `cleanedHtml` (shown in the
  reader) and `cleanedText` (paragraphs, used for listening and search). Some sites
  refuse non-browser requests (e.g. Slashdot answers HTTP 403) or show a paywall;
  the reader then says why it couldn't clean.
- **Read aloud** (`tts/`): Android TextToSpeech, one app-wide `ArticleSpeaker`.
  Reads the version on screen (cleaned if shown), title first, in chunks the engine
  accepts. Language per article from its text (Spanish/English function words,
  plus ñ/accents/¿¡ as strong Spanish evidence), falling back to the feed's
  declared language (often wrong: many Spanish blogs declare en-US). An ES/EN
  button in the now-playing bar switches the voice mid-article and remembers the
  choice per source (feed, or website for in-app pages), which then wins over
  detection. A "now playing" bar above the bottom bar
  shows progress, speed − / + (0.5× to 3×, remembered), pause/resume and stop on
  every screen. Speed changes and pause/resume continue from the start of the
  sentence being read (or the current word in a very long sentence), using the
  engine's word-position callbacks. Not yet: a foreground
  media service (so reading may stop if Android kills the app in the background),
  lock-screen controls.
  - **Edit group**: rename, delete, add a source by URL, remove sources
  - **Sources**: add by URL (optionally into groups); tap a source for its articles;
    its menu changes groups, refreshes, or deletes. Each source shows when it was
    last updated, unread/total articles, or the last error. The top bar's menu
    exports all sources and groups to an OPML file, or imports one (from any
    reader): groups become OPML folders; import only adds (new feeds are fetched,
    missing groups created, existing feeds just joined to the file's groups).
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
5. **TTS** - read-aloud with speed control done. Still to do: foreground media
   service with lock-screen/notification controls, sentence highlighting.
6. **Polish** - OPML import/export and article search done; offline reading to do.

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
