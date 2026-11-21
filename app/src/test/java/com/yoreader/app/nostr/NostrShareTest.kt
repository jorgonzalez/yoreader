package com.yoreader.app.nostr

import com.yoreader.app.nostr.NostrRelay.PublishResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrShareTest {
    private val key = NostrKey.generate()
    private val url = "https://www.example.com/news/story.html?id=7&utm_source=rss&utm_medium=feed#comments"

    @Test
    fun cleansLinks() {
        assertEquals("https://www.example.com/news/story.html?id=7", NostrShare.cleanUrl(url))
        assertEquals("https://a.example/x", NostrShare.cleanUrl("https://a.example/x?fbclid=abc"))
        assertEquals("www.example.com/news/story.html?id=7", NostrShare.address(url))
        assertEquals("http://a.example/é", NostrShare.cleanUrl("http://a.example/é"))
    }

    @Test
    fun bookmarkFollowsNipB0() {
        val e = NostrShare.bookmark(key, url, " The story ", " Worth reading ", now = 1700000000)
        assertEquals(39701, e.kind)
        assertEquals("Worth reading", e.content)
        assertEquals("www.example.com/news/story.html?id=7", e.tag("d"))
        assertEquals("1700000000", e.tag("published_at"))
        assertEquals("The story", e.tag("title"))
        assertTrue(e.isValid())
    }

    @Test
    fun noteHasCommentTitleAndLink() {
        val e = NostrShare.note(key, url, "The story", "Worth reading", now = 1700000000)
        assertEquals(1, e.kind)
        assertEquals("Worth reading\n\nThe story\n\nhttps://www.example.com/news/story.html?id=7", e.content)
        assertEquals("https://www.example.com/news/story.html?id=7", e.tag("r"))
        assertEquals("The story\n\nhttps://a.example/x", NostrShare.note(key, "https://a.example/x", "The story", "").content)
        assertTrue(e.isValid())
    }

    @Test
    fun summarisesRelayAnswers() {
        assertEquals(
            "Shared on Nostr (2 of 3 relays)",
            NostrShare.summary(listOf("a" to PublishResult.Accepted, "b" to PublishResult.Failed("x"), "c" to PublishResult.Accepted)),
        )
        assertEquals(
            "Couldn't share on Nostr: blocked: spam",
            NostrShare.summary(listOf("a" to PublishResult.Rejected("blocked: spam"))),
        )
        assertEquals(
            "Couldn't reach the Nostr relays. Check the connection and try again.",
            NostrShare.summary(listOf("a" to PublishResult.Failed("Unable to resolve host"), "b" to PublishResult.Failed("x"))),
        )
    }

    @Test
    fun relayAddresses() {
        assertEquals("wss://relay.example.com", NostrShare.normalizeRelay(" relay.example.com/ "))
        assertEquals("wss://relay.example.com", NostrShare.normalizeRelay("WSS://Relay.Example.com"))
        assertEquals("wss://relay.example.com:4848/inbox", NostrShare.normalizeRelay("wss://relay.example.com:4848/inbox"))
        assertEquals("ws://localhost:7777", NostrShare.normalizeRelay("ws://localhost:7777"))
        assertEquals(null, NostrShare.normalizeRelay("https://relay.example.com"))
        assertEquals(null, NostrShare.normalizeRelay("relay"))
        assertEquals(null, NostrShare.normalizeRelay("two words.com"))
    }

    @Test
    fun relayListNamesEachRelay() {
        val e = NostrShare.relayList(key, listOf("wss://a.example", "wss://b.example"), now = 5)
        assertEquals(10002, e.kind)
        assertEquals(listOf(listOf("r", "wss://a.example"), listOf("r", "wss://b.example")), e.tags)
        assertEquals(listOf("wss://a.example", "wss://b.example"), NostrSource.writeRelays(e))
        assertTrue(e.isValid())
    }
}
