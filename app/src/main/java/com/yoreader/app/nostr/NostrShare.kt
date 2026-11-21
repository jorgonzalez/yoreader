package com.yoreader.app.nostr

import java.net.URI

/** The events published when you share an article on Nostr. */
object NostrShare {
    /** NIP-B0 web bookmark: what yoreader reads back from the people you follow. */
    const val KIND_WEB_BOOKMARK = 39701
    /** A plain note, shown by every Nostr app. */
    const val KIND_NOTE = 1

    /**
     * A web bookmark (NIP-B0). It's "addressable": its "d" tag (the address without
     * "https://") names it, so sharing the same article again replaces the old one
     * instead of adding a duplicate.
     */
    fun bookmark(key: NostrKey, url: String, title: String, comment: String, now: Long = nowSeconds()): NostrEvent {
        val tags = buildList {
            add(listOf("d", address(url)))
            add(listOf("published_at", now.toString()))
            if (title.isNotBlank()) add(listOf("title", title.trim()))
            add(listOf("client", "yoreader"))
        }
        return NostrEvent.sign(key, KIND_WEB_BOOKMARK, comment.trim(), tags, now)
    }

    /** A note with the comment, the title and the link, for people on other Nostr apps. */
    fun note(key: NostrKey, url: String, title: String, comment: String, now: Long = nowSeconds()): NostrEvent {
        val text = listOf(comment.trim(), title.trim(), cleanUrl(url)).filter { it.isNotEmpty() }.joinToString("\n\n")
        val tags = listOf(listOf("r", cleanUrl(url)), listOf("client", "yoreader"))
        return NostrEvent.sign(key, KIND_NOTE, text, tags, now)
    }

    /** The link, without its "#fragment" and tracking parameters (utm_…, fbclid…). */
    fun cleanUrl(url: String): String {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return url.trim()
        }
        val query = uri.rawQuery?.split('&')
            ?.filter { p -> TRACKING.none { p.substringBefore('=').lowercase().startsWith(it) } }
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }
        return URI(uri.scheme, uri.rawAuthority, uri.rawPath, null, null).toString()
            .let { base -> if (query != null) "$base?$query" else base }
    }

    /** NIP-B0's "d" tag: the clean link without "https://" (or "http://"). */
    fun address(url: String): String = cleanUrl(url).substringAfter("://")

    /** A one-line summary of the relays' answers, for a message. */
    fun summary(results: List<Pair<String, NostrRelay.PublishResult>>): String {
        val ok = results.count { it.second == NostrRelay.PublishResult.Accepted }
        if (ok > 0) return "Shared on Nostr ($ok of ${results.size} relays)"
        // A relay's own refusal says why (e.g. "rate-limited: …"); network errors don't help.
        val refusal = results.firstNotNullOfOrNull { (it.second as? NostrRelay.PublishResult.Rejected)?.message }
        return if (refusal != null) {
            "Couldn't share on Nostr: $refusal"
        } else {
            "Couldn't reach the Nostr relays. Check the connection and try again."
        }
    }

    private val TRACKING = listOf("utm_", "fbclid", "gclid", "mc_cid", "mc_eid")

    private fun nowSeconds() = System.currentTimeMillis() / 1000
}
