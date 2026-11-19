# Laptop sync file

The phone and the laptop page (`web/`) share one JSON file, e.g. `yoreader-sync.json`
in Google Drive. The phone chooses it in Options → Laptop (Android file picker); the
laptop page chooses the same file through the Google Drive for desktop folder. No
server is involved: each side reads the file, takes the newer changes and writes it.

```json
{
  "writtenBy": "phone",
  "writtenAt": 1763460000000,
  "format": "yoreader-sync",
  "version": 1,
  "groups": ["News", "Tech"],
  "feeds": [
    { "url": "https://example.org/feed.xml", "title": "Example", "siteUrl": "https://example.org", "groups": ["News"] }
  ],
  "articles": [
    {
      "feed": "https://example.org/feed.xml",
      "guid": "https://example.org/a1",
      "title": "Title",
      "link": "https://example.org/a1",
      "published": 1763450000000,
      "excerpt": "Plain text, up to 300 characters",
      "html": "<p>Article text (unread and saved articles only)</p>",
      "read": false, "readAt": null,
      "saved": false, "savedAt": null
    }
  ]
}
```

- Times are milliseconds since 1970 (UTC). Optional fields may be missing or `null`.
- An article is identified by `feed` (its feed URL) + `guid`.
- `readAt` / `savedAt`: when that state last changed, on either side. For each article
  and each of the two states, the newest change wins. `null` means it never changed
  since sync existed, so any dated change wins over it.
- The phone owns the content: feeds, groups and which articles are in the file
  (unread, saved, and read in the last 7 days; text only for unread and saved).
- The laptop only changes `read`/`readAt`/`saved`/`savedAt`, `addFeeds` and
  `removeFeeds` (and sets
  `writtenBy` to `"web"`). It keeps its own changes in the browser until a write by the phone
  includes them, so a phone write that crossed with the laptop's loses nothing.
- `writtenBy` and `writtenAt` come first in the file: the phone reads only the first
  200 bytes to see whether the file is still its own last write.
- Sources added on the laptop: the laptop writes `"addFeeds": [{ "url", "groups",
  "at" }]` (`url` as typed, cleaned up like the app does; `groups` by name, missing
  ones are created; `at` identifies the request). The phone adds them, then lists
  the requests it took for 30 days as `"addedFeeds": [{ "url", "at" }]`. The laptop
  asks again in each write until it sees its request there (or the source in those
  groups), since the source's URL may change (a website address becomes its feed's).
- Sources removed on the laptop work the same way: `"removeFeeds": [{ "url", "at" }]`
  (the source's `url` from the file); the phone removes the source and its articles
  and lists the requests as `"removedFeeds"` for 30 days. The laptop stops asking
  when it sees its request there or the source is gone.
- A feed may have `"error"`: why its last refresh failed.
- A reader must refuse a file with a higher `version` than it knows.

`sample.json` is read by the tests on both sides (`app/src/test/.../sync/SyncFileTest.kt`
and `web/test/sync.test.js`).
