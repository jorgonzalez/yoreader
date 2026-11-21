package com.yoreader.app.nostr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrEventTest {
    private val key = NostrKey.parse("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5")!!

    @Test
    fun serializesAsNip01Says() {
        val s = NostrEvent.serialize(
            pubkey = "ab",
            createdAt = 1700000000,
            kind = 39701,
            tags = listOf(listOf("d", "example.com/a/b"), listOf("title", "Él dijo \"hola\"")),
            content = "line 1\nline 2\ttab \\ back </script> ñ 🎉",
        )
        assertEquals(
            "[0,\"ab\",1700000000,39701,[[\"d\",\"example.com/a/b\"],[\"title\",\"Él dijo \\\"hola\\\"\"]]," +
                "\"line 1\\nline 2\\ttab \\\\ back </script> ñ 🎉\"]",
            s,
        )
    }

    @Test
    fun signedEventsCheckOutAndTamperingIsCaught() {
        val event = NostrEvent.sign(key, kind = 1, content = "hello", tags = listOf(listOf("t", "test")), createdAt = 1700000000)
        assertEquals(key.publicKeyHex, event.pubkey)
        assertTrue(event.isValid())
        assertFalse(event.copy(content = "hello!").isValid())
        assertFalse(event.copy(createdAt = 1700000001).isValid())
        assertFalse(event.copy(pubkey = NostrKey.generate().publicKeyHex).isValid())
        assertFalse(event.copy(sig = "00".repeat(64)).isValid())
    }

    @Test
    fun survivesAJsonRoundTrip() {
        val event = NostrEvent.sign(key, kind = 1, content = "a/b \"c\"", createdAt = 1700000000)
        val back = NostrEvent.fromJson(JSONObject(event.toJson().toString()))!!
        assertEquals(event, back)
        assertTrue(back.isValid())
        assertEquals(null, NostrEvent.fromJson(JSONObject("{\"id\":\"x\"}")))
    }

    @Test
    fun acceptsARealEventFromARelay() {
        // A real kind-1 note fetched from relay.damus.io, signed by another client: if
        // our serialization differed from the spec in any way, its id wouldn't match.
        val json = javaClass.getResource("/nostr/real-event.json")!!.readText()
        val event = NostrEvent.fromJson(JSONObject(json))!!
        assertTrue(event.isValid())
    }
}
