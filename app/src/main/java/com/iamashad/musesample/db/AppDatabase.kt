package com.iamashad.musesample.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.iamashad.musesample.db.dao.SessionDao
import com.iamashad.musesample.db.entities.SessionEntity

/**
 * Encrypted Room database (see [DbProvider] for SQLCipher wiring).
 *
 * Entities:
 *  - [SessionEntity]: one row per recorded PCG session (metadata + file paths).
 *
 * Schema versioning:
 *  - version = 1 (no migrations yet).
 *  - exportSchema = true to allow Room to write schema for tooling.
 */
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** Entry point for all session queries and writes. */
    abstract fun sessionDao(): SessionDao
}
