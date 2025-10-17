package com.iamashad.musesample.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import androidx.core.content.edit

object DbKeyStore {
    private const val PREFS = "secure_prefs"
    private const val KEY = "db_passphrase_b64"

    fun getOrCreatePassphrase(context: Context): ByteArray {
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

        prefs.getString(KEY, null)?.let { b64 ->
            return Base64.decode(b64, Base64.NO_WRAP)
        }

        val bytes = ByteArray(32) // 256-bit
        SecureRandom().nextBytes(bytes)
        prefs.edit { putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        return bytes
    }
}
