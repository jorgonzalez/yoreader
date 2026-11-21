package com.yoreader.app.nostr

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the Nostr private key on this phone, encrypted with a key from the Android
 * Keystore (which never leaves the phone's secure hardware). Its preferences file is
 * left out of Android's cloud backups (res/xml/backup_rules.xml): restored elsewhere it
 * couldn't be decrypted anyway, so the way to move it is the nsec backup.
 */
class NostrKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _key = MutableStateFlow(load())
    /** Null until a key is created or restored. */
    val key: StateFlow<NostrKey?> = _key.asStateFlow()

    fun create(): NostrKey = save(NostrKey.generate())

    fun restore(key: NostrKey): NostrKey = save(key)

    fun remove() {
        prefs.edit().remove(SECRET).apply()
        _key.value = null
    }

    private fun save(key: NostrKey): NostrKey {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val secret = key.secretBytes()
        val sealed = cipher.iv + cipher.doFinal(secret)
        secret.fill(0)
        prefs.edit().putString(SECRET, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
        _key.value = key
        return key
    }

    private fun load(): NostrKey? {
        val stored = prefs.getString(SECRET, null) ?: return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
            val secret = cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            NostrKey.fromBytes(secret).also { secret.fill(0) }
        } catch (e: Exception) {
            // E.g. the Keystore key is gone (data restored onto another phone): no key.
            null
        }
    }

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "nostr"
        const val SECRET = "secret"
        const val ALIAS = "yoreader-nostr"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
