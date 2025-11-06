package com.iamashad.musesample.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iamashad.musesample.security.DbKeyStore.getOrCreatePassphrase
import com.iamashad.musesample.utils.TAG_MUSE_SEC
import java.security.SecureRandom

/**
 * Manages the SQLCipher database passphrase.
 *
 * How it works:
 * - Creates a 32-byte random key once and stores it in EncryptedSharedPreferences.
 * - On subsequent launches, loads and returns the same key.
 * - Uses AndroidX Security Crypto:
 *     • MasterKey (AES-256-GCM) in keystore to protect the preferences file key.
 *     • EncryptedSharedPreferences with AES-256-SIV (keys) and AES-256-GCM (values).
 *
 * Consumers:
 * - DbProvider.build() calls [getOrCreatePassphrase] and hands the returned bytes to
 *   SQLCipher's SupportOpenHelperFactory.
 */
object DbKeyStore {
    private const val PREFS = "secure_prefs"
    private const val KEY = "db_passphrase_b64"

    /**
     * Returns the persistent 32-byte DB passphrase.
     * Generates and stores it securely the first time.
     */
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val t0 = System.currentTimeMillis()

        // Master key material stored in Android Keystore; used to encrypt the prefs file key.
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Encrypted preferences: metadata (keys) via AES-SIV, values via AES-GCM.
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Return existing key if present.
        prefs.getString(KEY, null)?.let {
            Log.i(TAG_MUSE_SEC, "db_key_loaded | MS=${System.currentTimeMillis() - t0}")
            return Base64.decode(it, Base64.NO_WRAP)
        }

        // First run: create a cryptographically strong 32-byte passphrase.
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)

        // Store base64-encoded to avoid binary issues in prefs.
        prefs.edit {
            putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }

        Log.i(TAG_MUSE_SEC, "db_key_created | BYTES=32 | MS=${System.currentTimeMillis() - t0}")
        return bytes
    }
}
