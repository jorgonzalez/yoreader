package com.yoreader.app.nostr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Talks to several relays at once: publishes to all (so a share survives relays that
 * drop it or go away), and reads from all, keeping each event once and only if its
 * id and signature check out (relays can't be trusted to filter forgeries).
 */
class NostrClient(httpClient: OkHttpClient, relays: List<String> = DEFAULT_RELAYS) {

    /** No overall call timeout: a WebSocket stays open while we wait for answers. */
    private val client = httpClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    var relays: List<NostrRelay> = relays.map { NostrRelay(it, client) }
        private set

    fun setRelays(urls: List<String>) {
        relays = urls.map { NostrRelay(it, client) }
    }

    /** Each relay's answer to [event], in relay order. */
    suspend fun publish(event: NostrEvent): List<Pair<String, NostrRelay.PublishResult>> = coroutineScope {
        relays.map { relay -> async { relay.url to relay.publish(event) } }.awaitAll()
    }

    data class FetchResult(val events: List<NostrEvent>, val failedRelays: Map<String, String>)

    /**
     * Valid events matching [filters] from all relays, newest first, each once.
     * Relays that couldn't be reached are listed in [FetchResult.failedRelays].
     */
    suspend fun fetch(filters: List<NostrFilter>): FetchResult = coroutineScope {
        val answers = relays.map { relay ->
            async { relay.url to runCatching { relay.fetch(filters) } }
        }.awaitAll()
        val failed = answers.mapNotNull { (url, r) -> r.exceptionOrNull()?.let { url to (it.message ?: "failed") } }.toMap()
        val events = answers.flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .filter { it.isValid() }
            .sortedByDescending { it.createdAt }
        FetchResult(events, failed)
    }

    /** Which relays answer at all (a request that asks for nothing). */
    suspend fun check(): List<Pair<String, String?>> = coroutineScope {
        relays.map { relay ->
            async {
                relay.url to runCatching { relay.fetch(listOf(NostrFilter(limit = 0)), timeoutMs = 8_000) }
                    .exceptionOrNull()?.let { it.message?.removePrefix(relay.url + ": ") ?: "failed" }
            }
        }.awaitAll()
    }

    companion object {
        /** Large, long-running public relays; changeable in Options later. */
        val DEFAULT_RELAYS = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
    }
}
