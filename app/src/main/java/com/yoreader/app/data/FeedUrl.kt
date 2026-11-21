package com.yoreader.app.data

import com.yoreader.app.nostr.NostrSource
import java.net.URI
import java.net.URISyntaxException

/**
 * Helpers for the URLs users type in when adding a source. Besides web addresses, a
 * Nostr public key ("npub1…") is a source too: the articles that person shares.
 */
object FeedUrl {

    /**
     * Returns a cleaned-up http(s) URL, or null if the input isn't a usable URL.
     * A missing scheme is treated as https, so "example.com/feed.xml" works.
     */
    fun normalize(input: String): String? {
        NostrSource.normalize(input)?.let { return it }
        var s = input.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (!s.contains("://")) s = "https://$s"
        val uri = try {
            URI(s)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        if (!host.contains('.')) return null
        return s
    }

    /** Key for duplicate detection: case-insensitive, ignoring a trailing slash. */
    fun key(url: String): String = url.lowercase().trimEnd('/')

    /**
     * Placeholder title until feed fetching can read the real one from the feed itself.
     */
    fun defaultTitle(url: String): String {
        NostrSource.npubOf(url)?.let { return NostrSource.shortNpub(it) }
        val host = try {
            URI(url).host
        } catch (e: URISyntaxException) {
            null
        }
        return host?.removePrefix("www.") ?: url
    }
}
