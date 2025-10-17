package com.iamashad.musesample.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
        // Load native SQLCipher lib from AAR
        System.loadLibrary("sqlcipher")

        val passphrase = DbKeyStore.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        val dbFile = context.getDatabasePath("muse.db")
        return Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .openHelperFactory(factory)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigrationOnDowngrade(false)
            .build()
    }
}
