package com.iamashad.musesample.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iamashad.musesample.TAG_MUSE_SEC
import java.security.SecureRandom

object DbKeyStore {
    private const val PREFS = "secure_prefs"
    private const val KEY = "db_passphrase_b64"

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val t0 = System.currentTimeMillis()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.getString(KEY, null)?.let {
            Log.i(TAG_MUSE_SEC, "db_key_loaded | MS=${System.currentTimeMillis() - t0}")
            return Base64.decode(it, Base64.NO_WRAP)
        }

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        prefs.edit { putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        Log.i(TAG_MUSE_SEC, "db_key_created | BYTES=32 | MS=${System.currentTimeMillis() - t0}")
        return bytes
    }

}
