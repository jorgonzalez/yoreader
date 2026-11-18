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
}
