package com.yoreader.app.nostr

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The relay protocol against a fake relay (OkHttp's MockWebServer). */
class NostrRelayTest {
    private val key = NostrKey.generate()
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() = servers.forEach { it.shutdown() }

    /** A relay that answers each message with [reply]'s lines. */
    private fun relay(reply: (JSONArray) -> List<String>): String {
        val server = MockWebServer()
        repeat(5) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    reply(JSONArray(text)).forEach { webSocket.send(it) }
                }

                override fun onOpen(webSocket: WebSocket, response: Response) = Unit

                // Answer the client's close, as a real relay does.
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            }))
        }
        server.start()
        servers += server
        return server.url("/").toString().replace("http://", "ws://")
    }

    private val http = OkHttpClient()

    @Test
    fun publishWaitsForTheRelaysOk() = runBlocking {
        val accepting = relay { msg ->
            if (msg.getString(0) == "EVENT") listOf("""["OK","${msg.getJSONObject(1).getString("id")}",true,""]""") else emptyList()
        }
        val refusing = relay { msg ->
            if (msg.getString(0) == "EVENT") listOf("""["OK","${msg.getJSONObject(1).getString("id")}",false,"blocked: no"]""") else emptyList()
        }
        val event = NostrEvent.sign(key, kind = 1, content = "hi")
        assertEquals(NostrRelay.PublishResult.Accepted, NostrRelay(accepting, http).publish(event))
        assertEquals(NostrRelay.PublishResult.Rejected("blocked: no"), NostrRelay(refusing, http).publish(event))
    }

    @Test
    fun publishGivesUpOnASilentRelay() = runBlocking {
        val silent = relay { emptyList() }
        val result = NostrRelay(silent, http).publish(NostrEvent.sign(key, 1, "hi"), timeoutMs = 500)
        assertTrue(result is NostrRelay.PublishResult.Failed)
    }

    @Test
    fun fetchCollectsEventsUntilEose() = runBlocking {
        val a = NostrEvent.sign(key, 1, "a", createdAt = 1000)
        val b = NostrEvent.sign(key, 1, "b", createdAt = 2000)
        val url = relay { msg ->
            if (msg.getString(0) != "REQ") return@relay emptyList()
            val sub = msg.getString(1)
            assertEquals(key.publicKeyHex, msg.getJSONObject(2).getJSONArray("authors").getString(0))
            listOf(
                """["EVENT","$sub",${a.toJson()}]""",
                """["EVENT","other",${b.toJson()}]""", // another subscription: ignored
                """["EVENT","$sub",${b.toJson()}]""",
                """["EOSE","$sub"]""",
            )
        }
        val events = NostrRelay(url, http).fetch(listOf(NostrFilter(authors = listOf(key.publicKeyHex), kinds = listOf(1))))
        assertEquals(listOf(a, b), events)
    }

    @Test(expected = NostrException::class)
    fun fetchFromAnUnreachableRelayThrows() {
        runBlocking { NostrRelay("ws://127.0.0.1:1/", http).fetch(listOf(NostrFilter(limit = 1)), timeoutMs = 2_000) }
    }

    @Test(expected = NostrException::class)
    fun fetchFromASilentRelayThrows() {
        val silent = relay { emptyList() }
        runBlocking { NostrRelay(silent, http).fetch(listOf(NostrFilter(limit = 1)), timeoutMs = 500) }
    }

    @Test
    fun clientMergesRelaysAndDropsForgeries() = runBlocking {
        val old = NostrEvent.sign(key, 1, "old", createdAt = 1000)
        val new = NostrEvent.sign(key, 1, "new", createdAt = 2000)
        val forged = new.copy(content = "forged")
        fun answering(vararg events: NostrEvent) = relay { msg ->
            if (msg.getString(0) != "REQ") return@relay emptyList()
            val sub = msg.getString(1)
            events.map { """["EVENT","$sub",${it.toJson()}]""" } + """["EOSE","$sub"]"""
        }
        val client = NostrClient(http, listOf(answering(old, new), answering(new, forged), "ws://127.0.0.1:1/"))
        val result = client.fetch(listOf(NostrFilter(kinds = listOf(1))))
        assertEquals(listOf(new, old), result.events)
        assertEquals(setOf("ws://127.0.0.1:1/"), result.failedRelays.keys)
    }
}
