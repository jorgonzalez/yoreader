package com.yoreader.app.nostr

/**
 * Bech32 (BIP-173), as Nostr uses it for keys (NIP-19): "npub1…" for a public key,
 * "nsec1…" for a private one. Only the plain 32-byte forms are needed here.
 */
internal object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun encode(prefix: String, data: ByteArray): String {
        val values = convertBits(data, 8, 5, pad = true)
        val checksum = checksum(prefix, values)
        return buildString {
            append(prefix).append('1')
            (values + checksum).forEach { append(CHARSET[it]) }
        }
    }

    /** The prefix and the data, or null if [text] isn't valid bech32. */
    fun decode(text: String): Pair<String, ByteArray>? {
        if (text.lowercase() != text && text.uppercase() != text) return null // mixed case
        val s = text.lowercase()
        val sep = s.lastIndexOf('1')
        if (sep < 1 || sep + 7 > s.length) return null
        val prefix = s.substring(0, sep)
        val values = IntArray(s.length - sep - 1) { CHARSET.indexOf(s[sep + 1 + it]) }
        if (values.any { it < 0 }) return null
        if (polymod(expand(prefix) + values) != 1) return null
        val data = convertBits(values.copyOfRange(0, values.size - 6), 5, 8, pad = false) ?: return null
        return prefix to ByteArray(data.size) { data[it].toByte() }
    }

    private fun checksum(prefix: String, values: IntArray): IntArray {
        val mod = polymod(expand(prefix) + values + IntArray(6)) xor 1
        return IntArray(6) { (mod shr (5 * (5 - it))) and 31 }
    }

    private fun expand(prefix: String): IntArray =
        prefix.map { it.code shr 5 }.toIntArray() + intArrayOf(0) + prefix.map { it.code and 31 }.toIntArray()

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0 until 5) if ((top shr i) and 1 == 1) chk = chk xor GENERATOR[i]
        }
        return chk
    }

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray =
        convertBits(IntArray(data.size) { data[it].toInt() and 0xff }, from, to, pad)!!

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val out = mutableListOf<Int>()
        val max = (1 shl to) - 1
        for (v in data) {
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out += (acc shr bits) and max
            }
        }
        if (pad) {
            if (bits > 0) out += (acc shl (to - bits)) and max
        } else if (bits >= from || ((acc shl (to - bits)) and max) != 0) {
            return null
        }
        return out.toIntArray()
    }
}
