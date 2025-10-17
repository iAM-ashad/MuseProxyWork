package com.iamashad.musesample.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.iamashad.musesample.db.dao.SessionDao
import com.iamashad.musesample.db.entities.SessionEntity

@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
