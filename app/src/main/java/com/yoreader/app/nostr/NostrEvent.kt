package com.yoreader.app.nostr

import fr.acinq.secp256k1.Secp256k1
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A signed Nostr event (NIP-01). [id] is the SHA-256 of the event's canonical form
 * ([serialize]), [sig] a BIP-340 Schnorr signature of [id] by [pubkey]. All hex.
 */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** The first value of the first tag named [name], e.g. "d" or "title". */
    fun tag(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

    /** Whether [id] matches the content and [sig] is [pubkey]'s signature of it. */
    fun isValid(): Boolean = try {
        id.length == 64 && pubkey.length == 64 && sig.length == 128 &&
            id == idOf(pubkey, createdAt, kind, tags, content) &&
            Secp256k1.verifySchnorr(sig.hexToBytes(), id.hexToBytes(), pubkey.hexToBytes())
    } catch (e: Exception) {
        false
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("pubkey", pubkey)
        .put("created_at", createdAt)
        .put("kind", kind)
        .put("tags", JSONArray(tags.map { JSONArray(it) }))
        .put("content", content)
        .put("sig", sig)

    companion object {
        /** Signs a new event by [key]; [createdAt] in Unix seconds. */
        fun sign(
            key: NostrKey,
            kind: Int,
            content: String,
            tags: List<List<String>> = emptyList(),
            createdAt: Long = System.currentTimeMillis() / 1000,
        ): NostrEvent {
            val pubkey = key.publicKeyHex
            val id = idOf(pubkey, createdAt, kind, tags, content)
            val secret = key.secretBytes()
            val aux = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val sig = Secp256k1.signSchnorr(id.hexToBytes(), secret, aux).toHex()
            secret.fill(0)
            return NostrEvent(id, pubkey, createdAt, kind, tags, content, sig)
        }

        /** From a relay's JSON; null if a field is missing or of the wrong type. */
        fun fromJson(o: JSONObject): NostrEvent? = try {
            val tags = o.getJSONArray("tags").let { arr ->
                List(arr.length()) { i -> arr.getJSONArray(i).let { t -> List(t.length()) { t.getString(it) } } }
            }
            NostrEvent(
                id = o.getString("id"),
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = tags,
                content = o.getString("content"),
                sig = o.getString("sig"),
            )
        } catch (e: Exception) {
            null
        }

        fun idOf(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(serialize(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8))
                .toHex()

        /**
         * NIP-01's canonical form: [0,pubkey,created_at,kind,tags,content], no spaces,
         * strings escaped exactly as the spec says (other characters as they are).
         * Written by hand because JSON libraries escape more (e.g. "/") and that would
         * change the id.
         */
        fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
            buildString {
                append("[0,").appendQuoted(pubkey).append(',').append(createdAt).append(',').append(kind).append(",[")
                tags.forEachIndexed { i, tag ->
                    if (i > 0) append(',')
                    append('[')
                    tag.forEachIndexed { j, v ->
                        if (j > 0) append(',')
                        appendQuoted(v)
                    }
                    append(']')
                }
                append("],").appendQuoted(content).append(']')
            }

        private fun StringBuilder.appendQuoted(s: String): StringBuilder {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> append(c)
                }
            }
            return append('"')
        }
    }
}

/** What to ask relays for (NIP-01 filter); null fields aren't sent. */
data class NostrFilter(
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        authors?.let { put("authors", JSONArray(it)) }
        kinds?.let { put("kinds", JSONArray(it)) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
        limit?.let { put("limit", it) }
    }
}
