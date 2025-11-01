package com.iamashad.musesample.db

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import com.iamashad.musesample.TAG_MUSE_DB
import com.iamashad.musesample.security.DbKeyStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object DbProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): AppDatabase {
        val t0 = System.currentTimeMillis()

        // Load native SQLCipher lib from AAR
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

        val passphrase = DbKeyStore.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        val dbFile = context.getDatabasePath("muse.db")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .openHelperFactory(factory)
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
