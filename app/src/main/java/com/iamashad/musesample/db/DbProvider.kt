package com.iamashad.musesample.db

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.iamashad.musesample.TAG_MUSE_DB
import com.iamashad.musesample.security.DbKeyStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Singleton creator for the encrypted Room database.
 *
 * How encryption works:
 * - A 32-byte passphrase is created/stored in [EncryptedSharedPreferences] via [DbKeyStore].
 * - SQLCipher’s [SupportOpenHelperFactory] uses that passphrase to encrypt the DB file.
 *
 * Behavior:
 * - Loads SQLCipher native library explicitly (helps catch packaging issues early).
 * - WAL (Write-Ahead Logging) is enabled for better performance on concurrent reads/writes.
 * - No destructive migration on downgrade (fails fast instead of silently wiping data).
 *
 * Usage:
 *   val db = DbProvider.get(context)
 *   val dao = db.sessionDao()
 */
object DbProvider {

    @Volatile
    private var instance: AppDatabase? = null

    /** Thread-safe lazy init; returns the process-wide DB instance. */
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /** Builds a fresh encrypted Room database instance. */
    private fun build(context: Context): AppDatabase {
        val t0 = System.currentTimeMillis()

        // Ensure SQLCipher shared object is available; surface a clear error if not.
        try {
            System.loadLibrary("sqlcipher")
            Log.i(TAG_MUSE_DB, "sqlcipher_loaded")
        } catch (t: Throwable) {
            Log.e(TAG_MUSE_DB, "sqlcipher_load_fail | REASON=${t.javaClass.simpleName}: ${t.message}")
            throw t
        }

        // Derive/get a stable passphrase and wire it into SQLCipher.
        val passphrase = DbKeyStore.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        val dbFile = context.getDatabasePath("muse.db")

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .openHelperFactory(factory)                              // <-- encryption hook
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()

        Log.i(
            TAG_MUSE_DB,
            "room_open_ok | WAL=on | PATH=${dbFile.absolutePath} | MS=${System.currentTimeMillis() - t0}"
        )
        return db
    }
}
