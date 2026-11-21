package com.yoreader.app.nostr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import java.util.UUID

/**
 * One relay, spoken to over a WebSocket (NIP-01). Each call opens a connection, does
 * its one job and closes it: yoreader publishes now and then and reads on refresh, so
 * there's no need to stay connected.
 */
class NostrRelay(val url: String, private val client: OkHttpClient) {

    sealed interface PublishResult {
        data object Accepted : PublishResult
        /** The relay said no; [message] is its reason, e.g. "blocked: …" or "rate-limited: …". */
        data class Rejected(val message: String) : PublishResult
        data class Failed(val message: String) : PublishResult
    }

    /** Sends [event] and waits for the relay's ["OK", …] answer. */
    suspend fun publish(event: NostrEvent, timeoutMs: Long = TIMEOUT_MS): PublishResult {
        val answer = CompletableDeferred<PublishResult>()
        val socket = open(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONArray().put("EVENT").put(event.toJson()).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = parse(text) ?: return
                if (msg.optString(0) == "OK" && msg.optString(1) == event.id) {
                    answer.complete(
                        if (msg.optBoolean(2)) PublishResult.Accepted
                        else PublishResult.Rejected(msg.optString(3).ifBlank { "refused" }),
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                answer.complete(PublishResult.Failed(failure(t, response)))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                answer.complete(PublishResult.Failed("closed before answering"))
            }
        })
        return try {
            withTimeoutOrNull(timeoutMs) { answer.await() } ?: PublishResult.Failed("no answer in ${timeoutMs / 1000} s")
        } finally {
            socket.close(1000, null)
        }
    }

    /**
     * Events matching [filters], up to the relay's "end of stored events" (EOSE) or
     * [timeoutMs]. Throws [NostrException] if the relay can't be reached at all.
     * Events aren't checked here; [NostrClient] does that.
     */
    suspend fun fetch(filters: List<NostrFilter>, timeoutMs: Long = TIMEOUT_MS): List<NostrEvent> {
        val subId = "yr-" + UUID.randomUUID().toString().take(8)
        val events = mutableListOf<NostrEvent>()
        val done = CompletableDeferred<String?>() // null: finished; otherwise the error
        val socket = open(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val req = JSONArray().put("REQ").put(subId)
                filters.forEach { req.put(it.toJson()) }
                webSocket.send(req.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = parse(text) ?: return
                if (msg.optString(1) != subId) return
                when (msg.optString(0)) {
                    "EVENT" -> msg.optJSONObject(2)?.let(NostrEvent::fromJson)?.let { synchronized(events) { events += it } }
                    "EOSE" -> done.complete(null)
                    "CLOSED" -> done.complete(msg.optString(2).ifBlank { "closed by relay" })
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                done.complete(failure(t, response))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                done.complete(null)
            }
        })
        val error = try {
            // Wrapped, to tell "finished" (null inside) from "no answer in time" (null).
            withTimeoutOrNull(timeoutMs) { listOf(done.await()) }?.first()
                ?: "no answer in ${timeoutMs / 1000} s".takeIf { !done.isCompleted }
        } finally {
            socket.send(JSONArray().put("CLOSE").put(subId).toString())
            socket.close(1000, null)
        }
        val got = synchronized(events) { events.toList() }
        // A failure or silence before anything arrived means the relay is unreachable;
        // after some events, keep what came (a timeout without EOSE also keeps them).
        if (error != null && got.isEmpty()) throw NostrException("$url: $error")
        return got
    }

    private fun open(listener: WebSocketListener): WebSocket =
        client.newWebSocket(Request.Builder().url(url).build(), listener)

    private fun parse(text: String): JSONArray? = try {
        JSONArray(text)
    } catch (e: Exception) {
        null
    }

    private fun failure(t: Throwable, response: Response?): String =
        response?.let { "HTTP ${it.code}" } ?: t.message ?: t.javaClass.simpleName

    companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

class NostrException(message: String) : Exception(message)
