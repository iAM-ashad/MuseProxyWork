package com.iamashad.musesample.security

import android.content.Context
import android.os.Build
import android.security.KeyStoreException
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iamashad.musesample.utils.TAG_MUSE_SEC
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Manages the SQLCipher database passphrase with full resilience against:
 * - AEADBadTagException
 * - KeyPermanentlyInvalidatedException
 * - Keystore corruption / device restore
 *
 * If the encrypted prefs cannot be decrypted, the master key + prefs
 * are deleted and recreated safely.
 */
object DbKeyStore {

    private const val PREFS = "secure_prefs"
    private const val KEY = "db_passphrase_b64"
    private const val MASTER_KEY_ALIAS = "master_key_aes256_gcm" // used internally by MasterKey

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val t0 = System.currentTimeMillis()

        return try {
            // Try normal path
            loadOrCreateKey(context).also {
                Log.i(TAG_MUSE_SEC, "db_key_ok | MS=${System.currentTimeMillis() - t0}")
            }

        } catch (e: Exception) {
            Log.w(TAG_MUSE_SEC, "db_key_error(${e::class.java.simpleName}): ${e.message}")

            // Recovery path: wipe corrupted master key + prefs and recreate
            try {
                cleanupKeystoreAndPrefs(context)
                loadOrCreateKey(context).also {
                    Log.w(TAG_MUSE_SEC, "db_key_recovered | MS=${System.currentTimeMillis() - t0}")
                }
            } catch (e2: Exception) {
                Log.e(TAG_MUSE_SEC, "db_key_FATAL: ${e2.message}", e2)
                throw RuntimeException("Fatal: Could not recover secure DB key", e2)
            }
        }
    }

    // ------------------------------------------------------------
    // Normal load-or-create logic
    // ------------------------------------------------------------
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun loadOrCreateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: AEADBadTagException) {
            throw e
        } catch (e: KeyStoreException) {
            throw e
        } catch (e: Throwable) {
            // Generic catch → treat as failure to decrypt
            throw e
        }

        // Existing key?
        prefs.getString(KEY, null)?.let { base64 ->
            return Base64.decode(base64, Base64.NO_WRAP)
        }

        // Create new 32-byte passphrase
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)

        prefs.edit {
            putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }

        return bytes
    }

    // ------------------------------------------------------------
    // Full recovery: delete corrupt master key + encrypted prefs
    // ------------------------------------------------------------
    private fun cleanupKeystoreAndPrefs(context: Context) {
        Log.w(TAG_MUSE_SEC, "db_key_cleanup: deleting master key + prefs")

        // 1. Delete encrypted SharedPreferences file
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            context.deleteSharedPreferences(PREFS)
        } catch (t: Throwable) {
            Log.w(TAG_MUSE_SEC, "Failed to delete prefs: ${t.message}")
        }

        // 2. Delete master key from Android Keystore
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            if (ks.containsAlias(MASTER_KEY_ALIAS)) {
                ks.deleteEntry(MASTER_KEY_ALIAS)
                Log.w(TAG_MUSE_SEC, "Deleted Keystore alias $MASTER_KEY_ALIAS")
            } else {
                Log.w(TAG_MUSE_SEC, "Keystore alias not found: $MASTER_KEY_ALIAS")
            }
        } catch (t: Throwable) {
            Log.w(TAG_MUSE_SEC, "Failed to delete keystore alias: ${t.message}")
        }
    }
}
