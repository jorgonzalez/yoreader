package com.yoreader.app.nostr

import fr.acinq.secp256k1.Secp256k1
import java.security.SecureRandom

/**
 * A Nostr identity: a secp256k1 private key and its public key (the 32-byte "x-only"
 * form of BIP-340, which Nostr uses everywhere). The private key never leaves the
 * phone except when you back it up yourself.
 */
class NostrKey private constructor(private val secret: ByteArray) {

    /** Public key, 32 bytes. */
    val publicKey: ByteArray = Secp256k1.pubkeyCreate(secret).copyOfRange(1, 33)

    /** Public key as Nostr clients show it: "npub1…". Safe to give to anyone. */
    val npub: String get() = Bech32.encode(NPUB, publicKey)

    /** Public key in hex, as in Nostr events. */
    val publicKeyHex: String get() = publicKey.toHex()

    /** Private key as "nsec1…", for backups. Whoever has it can post as you. */
    fun nsec(): String = Bech32.encode(NSEC, secret)

    internal fun secretBytes(): ByteArray = secret.copyOf()

    companion object {
        const val NPUB = "npub"
        const val NSEC = "nsec"

        /** A new random key. */
        fun generate(random: SecureRandom = SecureRandom()): NostrKey {
            val bytes = ByteArray(32)
            do random.nextBytes(bytes) while (!Secp256k1.secKeyVerify(bytes))
            return NostrKey(bytes)
        }

        /** From 32 raw bytes; null if they aren't a valid private key. */
        fun fromBytes(bytes: ByteArray): NostrKey? =
            if (bytes.size == 32 && Secp256k1.secKeyVerify(bytes)) NostrKey(bytes.copyOf()) else null

        /**
         * From a backup: "nsec1…" or 64 hex characters (spaces and a "nostr:" prefix
         * are ignored). Null if it isn't a private key, e.g. an npub pasted by mistake.
         */
        fun parse(text: String): NostrKey? {
            val s = text.trim().removePrefix("nostr:").replace(Regex("\\s"), "")
            if (s.length == 64 && s.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                return fromBytes(s.hexToBytes())
            }
            val (prefix, data) = Bech32.decode(s) ?: return null
            return if (prefix == NSEC) fromBytes(data) else null
        }

        /** Whether [text] is a public key ("npub1…"), e.g. pasted where a private one goes. */
        fun isNpub(text: String): Boolean =
            Bech32.decode(text.trim().removePrefix("nostr:"))?.let { it.first == NPUB && it.second.size == 32 } == true
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray = ByteArray(length / 2) { substring(2 * it, 2 * it + 2).toInt(16).toByte() }
