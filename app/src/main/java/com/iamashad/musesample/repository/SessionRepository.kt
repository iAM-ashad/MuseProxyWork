package com.iamashad.musesample.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.iamashad.musesample.db.DbProvider
import com.iamashad.musesample.db.entities.SessionEntity
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.utils.TAG_MUSE_DB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Process-wide repository for persisted PCG sessions.
 */
object SessionRepository {

    private lateinit var appContext: Context
    private val io = CoroutineScope(Dispatchers.IO)

    /** Initialize with the application context to avoid leaking activities. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Live list of sessions, newest-first, mapped from Room entities to domain models.
     */
    val sessions: Flow<List<Session>> by lazy {
        DbProvider.get(appContext).sessionDao().observeAll().map { rows ->
            rows.map { it.toDomain() }
        }
    }

    /** Insert or replace a session row. */
    fun add(s: Session) {
        io.launch {
            try {
                DbProvider.get(appContext).sessionDao().upsert(s.toEntity())
                Log.i(TAG_MUSE_DB, "session_upsert_ok | SID=${s.id}")
            } catch (t: Throwable) {
                Log.e(
                    TAG_MUSE_DB,
                    "session_upsert_fail | SID=${s.id} | REASON=${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

    /** Attach or update the generated PDF path for a session. */
    fun updatePdf(id: Long, pdfPath: String) {
        io.launch {
            try {
                DbProvider.get(appContext).sessionDao().updatePdfPath(id, pdfPath)
                val scheme = runCatching { pdfPath.toUri().scheme ?: "file" }.getOrDefault("file")
                Log.i(TAG_MUSE_DB, "session_pdf_update_ok | SID=$id | SCHEME=$scheme")
            } catch (t: Throwable) {
                Log.e(
                    TAG_MUSE_DB,
                    "session_pdf_update_fail | SID=$id | REASON=${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

    /** Delete a session row (used by the session card and bulk delete). */
    fun delete(s: Session) {
        io.launch {
            try {
                DbProvider.get(appContext).sessionDao().delete(s.toEntity())
                Log.i(TAG_MUSE_DB, "session_delete_ok | SID=${s.id}")
            } catch (t: Throwable) {
                Log.e(
                    TAG_MUSE_DB,
                    "session_delete_fail | SID=${s.id} | REASON=${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }
}

/* ------------------------------ Mappers ------------------------------ */

/**
 * Map a domain model to a Room entity.
 *
 * - Parses [Session.sessionStart] (e.g., "01 Nov 2025, 10:42") to epoch millis
 *   for stable sorting in SQL. Falls back to current time if parsing fails.
 */
private fun Session.toEntity(): SessionEntity {
    val epoch = try {
        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        val dt = LocalDateTime.parse(sessionStart, fmt)
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }

    return SessionEntity(
        id = id,
        patientName = patientName,
        patientId = patientId,
        age = age,
        sessionStartDisplay = sessionStart,
        sessionStartEpochMillis = epoch,
        deviceModel = deviceModel,
        notes = notes,
        posture = posture,
        position = position,
        wavPath = wavPath,
        rawWavPath = rawWavPath,
        pdfPath = pdfPath,
        sex = sex,
        height = height,
        weight = weight,
        bmi = bmi
    )
}

/** Map a Room entity back to the UI-facing domain model. */
private fun SessionEntity.toDomain(): Session = Session(
    id = id,
    patientName = patientName,
    patientId = patientId,
    age = age,
    sessionStart = sessionStartDisplay,
    deviceModel = deviceModel,
    notes = notes,
    posture = posture,
    position = position,
    wavPath = wavPath,
    rawWavPath = rawWavPath,
    pdfPath = pdfPath,
    sex = sex,
    height = height,
    weight = weight,
    bmi = bmi
)
