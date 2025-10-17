package com.iamashad.musesample.db.dao

import androidx.room.*
import com.iamashad.musesample.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY sessionStartEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("UPDATE sessions SET pdfPath = :pdfPath WHERE id = :id")
    suspend fun updatePdfPath(id: Long, pdfPath: String?)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
