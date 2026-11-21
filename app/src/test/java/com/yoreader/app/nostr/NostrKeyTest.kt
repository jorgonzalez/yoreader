package com.yoreader.app.nostr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrKeyTest {

    // Examples from NIP-19.
    private val npubExample = "npub10elfcs4fr0l0r8af98jlmgdh9c8tcxjvz9qkw038js35mp4dma8qzvjptg"
    private val npubHex = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
    private val nsecExample = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"
    private val nsecHex = "67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa"

    @Test
    fun bech32MatchesTheNip19Examples() {
        assertEquals(npubExample, Bech32.encode("npub", npubHex.hexToBytes()))
        assertEquals(nsecExample, Bech32.encode("nsec", nsecHex.hexToBytes()))
        val (prefix, data) = Bech32.decode(npubExample)!!
        assertEquals("npub", prefix)
        assertArrayEquals(npubHex.hexToBytes(), data)
    }

    @Test
    fun bech32RejectsTyposAndMixedCase() {
        assertNull(Bech32.decode(npubExample.replaceFirst('q', 'p')))
        assertNull(Bech32.decode(npubExample.dropLast(1)))
        assertNull(Bech32.decode("Npub" + npubExample.drop(4)))
        assertNotNull(Bech32.decode(npubExample.uppercase()))
    }

    @Test
    fun publicKeyFollowsBip340() {
        // BIP-340 test vector 0: private key 3.
        val key = NostrKey.fromBytes("0000000000000000000000000000000000000000000000000000000000000003".hexToBytes())!!
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", key.publicKeyHex)
    }

    @Test
    fun parsesBackupsInBothForms() {
        val fromNsec = NostrKey.parse("  nostr:$nsecExample\n")!!
        val fromHex = NostrKey.parse(nsecHex.uppercase())!!
        assertEquals(fromNsec.publicKeyHex, fromHex.publicKeyHex)
        assertEquals(nsecExample, fromNsec.nsec())
        // NIP-19's two examples are one key pair.
        assertEquals(npubExample, fromNsec.npub)
        assertEquals(fromNsec.npub, Bech32.encode("npub", fromNsec.publicKey))
    }

    @Test
    fun refusesWhatIsNotAPrivateKey() {
        assertNull(NostrKey.parse(npubExample))
        assertTrue(NostrKey.isNpub(npubExample))
        assertFalse(NostrKey.isNpub(nsecExample))
        assertNull(NostrKey.parse("hello"))
        assertNull(NostrKey.fromBytes(ByteArray(32))) // zero isn't a valid key
    }

    @Test
    fun generatedKeysAreValidAndDifferent() {
        val a = NostrKey.generate()
        val b = NostrKey.generate()
        assertEquals(32, a.publicKey.size)
        assertTrue(a.npub.startsWith("npub1"))
        assertFalse(a.publicKeyHex == b.publicKeyHex)
        assertEquals(a.publicKeyHex, NostrKey.parse(a.nsec())!!.publicKeyHex)
    }
}
