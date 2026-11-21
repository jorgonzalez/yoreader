package com.yoreader.app.nostr

import com.yoreader.app.data.FeedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrSourceTest {
    private val npub = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val alice = NostrKey.generate()

    @Test
    fun anNpubIsASourceAddress() {
        assertEquals("nostr:$npub", FeedUrl.normalize("  $npub "))
        assertEquals("nostr:$npub", FeedUrl.normalize("nostr:$npub"))
        assertEquals("nostr:$npub", FeedUrl.normalize(npub.uppercase()))
        assertNull(FeedUrl.normalize("npub1notvalid"))
        assertNull(NostrSource.normalize("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"))
        assertEquals("https://example.com/feed", FeedUrl.normalize("example.com/feed"))
        assertEquals("npub10elfcs4…zvjptg", FeedUrl.defaultTitle("nostr:$npub"))
        assertEquals("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e", NostrSource.publicKeyHex(npub))
        assertNull(NostrSource.npubOf("https://example.com/feed"))
    }

    @Test
    fun sharesBecomeArticlesNewestVersionOnly() {
        val first = NostrShare.bookmark(alice, "https://news.example/a", "Story A", "First take", now = 1000)
        val again = NostrShare.bookmark(alice, "https://news.example/a", "Story A", "Changed my mind", now = 3000)
        val other = NostrShare.bookmark(alice, "https://blog.example/b?x=1", "", "", now = 2000)
        val profile = NostrEvent.sign(alice, 0, """{"name":"alice","display_name":"Alice A."}""", createdAt = 500)
        val note = NostrShare.note(alice, "https://news.example/a", "Story A", "", now = 1000) // not a share

        val feed = NostrSource.toFeed(listOf(first, other, profile, again, note))
        assertEquals("Alice A.", feed.title)
        assertEquals(listOf("Story A", "blog.example/b?x=1"), feed.items.map { it.title })

        val a = feed.items[0]
        assertEquals("https://news.example/a", a.link)
        assertEquals("Changed my mind", a.summary)
        assertEquals(3_000_000L, a.publishedAt)
        assertEquals("nostr:39701:${alice.publicKeyHex}:news.example/a", a.guid)
        assertTrue(a.contentHtml!!.startsWith("<blockquote><p>Changed my mind</p></blockquote>"))

        val b = feed.items[1]
        assertNull(b.summary)
        assertEquals("<p><a href=\"https://blog.example/b?x=1\">blog.example</a></p>", b.contentHtml)
    }

    @Test
    fun commentsAreEscaped() {
        val e = NostrShare.bookmark(alice, "https://x.example/", "T", "<script>alert(1)</script>\n\nSecond & last", now = 1)
        val html = NostrSource.toFeed(listOf(e)).items.single().contentHtml!!
        assertTrue(html, html.contains("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p><p>Second &amp; last</p>"))
    }

    @Test
    fun aProfileWithoutNameLeavesTheTitleAlone() {
        val profile = NostrEvent.sign(alice, 0, """{"about":"hi"}""", createdAt = 1)
        assertNull(NostrSource.toFeed(listOf(profile)).title)
        assertNull(NostrSource.toFeed(listOf(NostrEvent.sign(alice, 0, "not json", createdAt = 1))).title)
    }

    @Test
    fun readsTheRelaysSomeoneWritesTo() {
        val list = NostrEvent.sign(
            alice, 10002, "",
            tags = listOf(
                listOf("r", "wss://their.relay/", "write"),
                listOf("r", "wss://both.relay"),
                listOf("r", "wss://inbox.relay", "read"),
                listOf("r", "https://not-a-relay.example"),
                listOf("r", "wss://their.relay"),
            ),
            createdAt = 1,
        )
        assertEquals(listOf("wss://their.relay", "wss://both.relay"), NostrSource.writeRelays(list))
    }
}
