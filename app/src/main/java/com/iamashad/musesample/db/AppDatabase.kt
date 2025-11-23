package com.iamashad.musesample.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iamashad.musesample.db.dao.SessionDao
import com.iamashad.musesample.db.entities.SessionEntity

/**
 * Encrypted Room database (see [DbProvider] for SQLCipher wiring).
 *
 * Entities:
 * - [SessionEntity]: one row per recorded PCG session (metadata + file paths).
 *
 * Schema versioning:
 * - version = 2 (Added rawWavPath)
 * - exportSchema = true to allow Room to write schema for tooling.
 */
@Database(
    entities = [SessionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** Entry point for all session queries and writes. */
    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * Migration from 1 to 2: Adds the 'rawWavPath' column.
         * We default it to an empty string for existing rows.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN rawWavPath TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}