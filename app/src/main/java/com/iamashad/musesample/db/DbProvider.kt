package com.iamashad.musesample.db

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.Room
import androidx.room.RoomDatabase
import com.iamashad.musesample.db.AppDatabase.Companion.MIGRATION_1_2
import com.iamashad.musesample.security.DbKeyStore
import com.iamashad.musesample.utils.TAG_MUSE_DB
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Encrypted Room database provider with robust recovery:
 * - If the current SQLCipher passphrase cannot open the DB, we delete the file
 *   and recreate a new clean DB instead of crashing.
 */
object DbProvider {

    @Volatile
    private var instance: AppDatabase? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun build(context: Context): AppDatabase {
        val t0 = System.currentTimeMillis()

        // Ensure SQLCipher native library is available
        try {
            System.loadLibrary("sqlcipher")
            Log.i(TAG_MUSE_DB, "sqlcipher_loaded")
        } catch (t: Throwable) {
            Log.e(
                TAG_MUSE_DB,
                "sqlcipher_load_fail | REASON=${t.javaClass.simpleName}: ${t.message}"
            )
            throw t
        }

        // Fetch stored passphrase (may trigger recovery logic inside DbKeyStore)
        val passphrase = DbKeyStore.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        val dbName = "muse.db"
        val dbFile = context.getDatabasePath(dbName)
        val dbPath = dbFile.absolutePath

        fun buildInternal(): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, dbPath)
                .openHelperFactory(factory) // encryption
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(false)
                .build()

        // Try to build DB
        return try {
            buildInternal()
        } catch (e: SQLiteException) {
            val msg = e.message ?: ""
            val isNotDb =
                msg.contains("file is not a database", ignoreCase = true) ||
                        msg.contains("file is encrypted", ignoreCase = true) ||
                        msg.contains("not an error", ignoreCase = true)

            if (isNotDb) {
                Log.w(
                    TAG_MUSE_DB,
                    "Encrypted DB corrupted or wrong key. Deleting and recreating. msg=$msg"
                )

                // Delete old corrupted/unencrypted DB
                context.deleteDatabase(dbName)

                // Recreate new clean DB
                val newDb = buildInternal()
                Log.w(TAG_MUSE_DB, "room_open_recovered | PATH=$dbPath")
                newDb
            } else {
                // Other SQLCipher issues → rethrow
                throw e
            }
        }.also {
            Log.i(
                TAG_MUSE_DB,
                "room_open_ok | WAL=on | PATH=$dbPath | MS=${System.currentTimeMillis() - t0}"
            )
        }
    }
}
