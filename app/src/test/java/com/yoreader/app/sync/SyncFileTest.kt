package com.yoreader.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class SyncFileTest {

    /** The sample shared with the laptop page's tests (web/test/sync.test.js). */
    private val sample = File("../sync/sample.json").readText()

    @Test
    fun readsTheSharedSample() {
        val c = SyncFile.parse(sample)
        assertEquals("phone", c.writtenBy)
        assertEquals(1763460000000L, c.writtenAt)
        assertEquals(3, c.statuses.size)
        val unread = c.statuses.first { it.guid == "a1" }
        assertEquals(false, unread.read)
        assertNull(unread.readAt)
        val saved = c.statuses.first { it.guid == "t1" }
        assertEquals("https://tech.example.com/rss", saved.feedUrl)
        assertEquals(true, saved.saved)
        assertEquals(1763436000000L, saved.savedAt)
    }

    @Test
    fun aMissingSavedFieldIsNotSaved() {
        val a2 = SyncFile.parse(sample).statuses.first { it.guid == "a2" }
        assertEquals(false, a2.saved)
        assertNull(a2.savedAt)
    }

    @Test
    fun peekFindsNothingInOtherText() {
        assertEquals(null to null, SyncFile.peek(""))
        assertEquals("web" to 7L, SyncFile.peek("{\"writtenBy\": \"web\", \"writtenAt\": 7, \"format\""))
    }

    @Test
    fun emptyFileHasNothing() {
        val c = SyncFile.parse("")
        assertNull(c.writtenBy)
        assertTrue(c.statuses.isEmpty())
    }

    @Test
    fun refusesOtherFilesAndNewerVersions() {
        for (text in listOf("not json", """{"format":"other"}""", """{"format":"yoreader-sync","version":99}""")) {
            try {
                SyncFile.parse(text)
                fail("accepted $text")
            } catch (e: SyncFile.FormatException) {
                // expected
            }
        }
    }

    @Test
    fun writesWhatItReads() {
        val body = SyncFile.body(
            groups = listOf("News"),
            feeds = listOf(SyncFile.FeedEntry("https://e.org/feed", "E", null, listOf("News"))),
            articles = listOf(
                SyncFile.ArticleEntry(
                    feedUrl = "https://e.org/feed", guid = "g1", title = "T", link = "https://e.org/1",
                    publishedAt = 5L, excerpt = "x", html = "<p>x</p>",
                    read = true, readAt = 10L, saved = false, savedAt = null,
                )
            ),
        )
        val text = SyncFile.write(body, now = 99L)
        val c = SyncFile.parse(text)
        assertEquals("phone", c.writtenBy)
        assertEquals(99L, c.writtenAt)
        assertEquals(listOf(SyncFile.Status("https://e.org/feed", "g1", true, 10L, false, null)), c.statuses)
        // Who wrote it comes first, so the phone can tell from the first bytes.
        assertTrue(text.startsWith("{\"writtenBy\":\"phone\",\"writtenAt\":99,"))
        assertEquals("phone" to 99L, SyncFile.peek(text.take(60)))
        // Fields without a value are left out, as the format allows.
        assertTrue(!text.contains("savedAt"))
        assertTrue(!text.contains("siteUrl"))
    }

    @Test
    fun readsSourcesAddedAndRemovedOnTheLaptop() {
        val text = """
            {"writtenBy":"web","writtenAt":5,"format":"yoreader-sync","version":1,"articles":[],
             "addFeeds":[
               {"url":"https://new.example.net/feed","groups":["Tech",""],"at":42},
               {"url":"","groups":[],"at":43},
               {"url":"https://no-time.example.net/feed","groups":[]},
               {"url":"example.com/rss","at":44}
             ],
             "removeFeeds":[{"url":"https://old.example.net/rss","at":45},{"at":46}]}
        """.trimIndent()
        assertEquals(
            listOf(
                SyncFile.FeedRequest("https://new.example.net/feed", listOf("Tech"), 42L),
                SyncFile.FeedRequest("example.com/rss", emptyList(), 44L),
            ),
            SyncFile.parse(text).addFeeds,
        )
        assertEquals(
            listOf(SyncFile.FeedRequest("https://old.example.net/rss", emptyList(), 45L)),
            SyncFile.parse(text).removeFeeds,
        )
        assertEquals(emptyList<SyncFile.FeedRequest>(), SyncFile.parse(sample).addFeeds)
        assertEquals(emptyList<SyncFile.FeedRequest>(), SyncFile.parse(sample).removeFeeds)
    }

    @Test
    fun writesTakenRequestsAndFeedErrors() {
        val body = SyncFile.body(
            groups = emptyList(),
            feeds = listOf(SyncFile.FeedEntry("https://e.org/feed", "E", null, emptyList(), "HTTP 404")),
            articles = emptyList(),
            addedFeeds = listOf(SyncFile.FeedRequest("e.org/feed", listOf("ignored"), 42L)),
            removedFeeds = listOf(SyncFile.FeedRequest("https://old.e.org/rss", emptyList(), 43L)),
        )
        val text = SyncFile.write(body, now = 99L)
        val root = org.json.JSONObject(text)
        assertEquals("HTTP 404", root.getJSONArray("feeds").getJSONObject(0).getString("error"))
        val added = root.getJSONArray("addedFeeds")
        assertEquals(1, added.length())
        assertEquals("e.org/feed", added.getJSONObject(0).getString("url"))
        assertEquals(42L, added.getJSONObject(0).getLong("at"))
        assertTrue(!added.getJSONObject(0).has("groups"))
        val removed = root.getJSONArray("removedFeeds")
        assertEquals("https://old.e.org/rss", removed.getJSONObject(0).getString("url"))
        assertEquals(43L, removed.getJSONObject(0).getLong("at"))
        val none = SyncFile.write(SyncFile.body(emptyList(), emptyList(), emptyList()), now = 1L)
        assertTrue(!none.contains("addedFeeds"))
        assertTrue(!none.contains("removedFeeds"))
    }
}
