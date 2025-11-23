package com.iamashad.musesample.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iamashad.musesample.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for persisted PCG sessions.
 *
 * Design:
 * - The list is observed as a Flow to update the UI automatically.
 * - Insert uses REPLACE so an incoming entity with the same [SessionEntity.id]
 *   overwrites the existing row (upsert semantics).
 */
@Dao
interface SessionDao {

    /**
     * Stream all sessions newest-first.
     *
     * Used by the Session History screen to stay live with DB changes.
     */
    @Query("SELECT * FROM sessions ORDER BY sessionStartEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /**
     * Insert or replace a session.
     *
     * Use when saving a newly captured session or updating a full row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    /**
     * Update only the PDF path for a given session.
     *
     * Keeps the row stable while attaching the generated report.
     */
    @Query("UPDATE sessions SET pdfPath = :pdfPath WHERE id = :id")
    suspend fun updatePdfPath(id: Long, pdfPath: String?)

    /** Delete an entire session row. */
    @Delete
    suspend fun delete(session: SessionEntity)

    /** Delete by primary key. Useful for bulk or ID-only paths. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
