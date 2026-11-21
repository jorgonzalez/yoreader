package com.yoreader.app.nostr

import com.yoreader.app.fetch.FetchResult
import com.yoreader.app.fetch.Html
import com.yoreader.app.fetch.ParsedFeed
import com.yoreader.app.fetch.ParsedItem
import org.json.JSONObject

/**
 * A person you follow on Nostr, as a source: the articles they share (NIP-B0 web
 * bookmarks). Stored like any feed, with the address "nostr:npub1…", so their shares
 * get read/saved state, groups, search and laptop sync like other articles.
 */
class NostrSource(private val client: NostrClient) {

    /** Their latest shares and their profile name, as a parsed feed. */
    suspend fun fetch(url: String): FetchResult {
        val npub = npubOf(url) ?: return FetchResult.Failure("Not a Nostr address")
        val pubkey = publicKeyHex(npub) ?: return FetchResult.Failure("Not a valid Nostr public key")
        // People publish to their own relays, listed in their relay list (NIP-65, the
        // "outbox model"): read that and the profile first, then ask those relays too.
        val about = client.fetch(listOf(NostrFilter(authors = listOf(pubkey), kinds = listOf(KIND_PROFILE, KIND_RELAY_LIST))))
        val theirRelays = about.events
            .filter { it.pubkey == pubkey && it.kind == KIND_RELAY_LIST }
            .maxByOrNull { it.createdAt }
            ?.let(::writeRelays).orEmpty()
            .take(MAX_THEIR_RELAYS)
        val shares = client.fetch(
            listOf(NostrFilter(authors = listOf(pubkey), kinds = listOf(NostrShare.KIND_WEB_BOOKMARK), limit = MAX_SHARES)),
            extraRelays = theirRelays,
        )
        val events = (about.events + shares.events).filter { it.pubkey == pubkey } // only this person's
        val reached = client.relays.size - about.failedRelays.size
        if (events.isEmpty() && reached == 0) return FetchResult.Failure("Couldn't reach the Nostr relays")
        return FetchResult.Success(toFeed(events), url, etag = null, lastModified = null)
    }

    companion object {
        const val PREFIX = "nostr:"
        const val KIND_PROFILE = 0
        const val KIND_RELAY_LIST = NostrShare.KIND_RELAY_LIST
        const val MAX_SHARES = 100
        /** Their own relays asked besides the defaults; more would slow refreshes down. */
        const val MAX_THEIR_RELAYS = 4

        /** The relays a relay list (kind 10002) says its author writes to, in its order. */
        fun writeRelays(list: NostrEvent): List<String> = list.tags
            .filter { it.size >= 2 && it[0] == "r" && (it.size == 2 || it[2].isBlank() || it[2] == "write") }
            .map { it[1].trim().trimEnd('/') }
            .filter { it.startsWith("wss://") }
            .distinct()

        /** "nostr:npub1…" for an npub (with or without "nostr:"), else null. */
        fun normalize(input: String): String? {
            val s = input.trim().removePrefix(PREFIX).lowercase()
            return if (NostrKey.isNpub(s)) PREFIX + s else null
        }

        /** The npub of a "nostr:npub1…" source address, else null. */
        fun npubOf(url: String): String? =
            url.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { NostrKey.isNpub(it) }

        fun publicKeyHex(npub: String): String? =
            Bech32.decode(npub)?.takeIf { it.first == NostrKey.NPUB && it.second.size == 32 }?.second?.toHex()

        /** "npub1abcdef…uvwxyz", the source's name until their profile is read. */
        fun shortNpub(npub: String): String = npub.take(12) + "…" + npub.takeLast(6)

        /**
         * Their shares as feed items, newest share first. A share is addressable (kind,
         * author, "d"): only its newest version counts, and the guid stays the same when
         * it's shared again, so the article is updated rather than added twice.
         */
        fun toFeed(events: List<NostrEvent>): ParsedFeed {
            val profile = events.filter { it.kind == KIND_PROFILE }.maxByOrNull { it.createdAt }
            val items = events
                .filter { it.kind == NostrShare.KIND_WEB_BOOKMARK && !it.tag("d").isNullOrBlank() }
                .groupBy { it.tag("d") }
                .values.map { versions -> versions.maxBy { it.createdAt } }
                .sortedByDescending { it.createdAt }
                .map(::toItem)
            return ParsedFeed(title = profile?.let(::profileName), siteUrl = null, language = null, items = items)
        }

        private fun toItem(e: NostrEvent): ParsedItem {
            val address = e.tag("d")!!
            val link = if (address.contains("://")) address else "https://$address"
            val comment = e.content.trim()
            val commentHtml = comment.split(Regex("\\n\\s*\\n"))
                .filter { it.isNotBlank() }
                .joinToString("") { "<p>" + Html.escape(it.trim()).replace("\n", "<br>") + "</p>" }
            val host = link.substringAfter("://").substringBefore('/').removePrefix("www.")
            val linkHtml = "<p><a href=\"${Html.escape(link)}\">${Html.escape(host)}</a></p>"
            return ParsedItem(
                guid = "$PREFIX${NostrShare.KIND_WEB_BOOKMARK}:${e.pubkey}:$address",
                title = e.tag("title")?.takeIf { it.isNotBlank() } ?: address,
                link = link,
                summary = comment.ifEmpty { null },
                contentHtml = (if (comment.isEmpty()) "" else "<blockquote>$commentHtml</blockquote>") + linkHtml,
                publishedAt = e.createdAt * 1000,
            )
        }

        /** "display_name", else "name", from a profile (kind 0, JSON content). */
        private fun profileName(e: NostrEvent): String? = try {
            val o = JSONObject(e.content)
            listOf("display_name", "displayName", "name")
                .map { o.optString(it).trim() }
                .firstOrNull { it.isNotEmpty() }
        } catch (ex: Exception) {
            null
        }
    }
}
