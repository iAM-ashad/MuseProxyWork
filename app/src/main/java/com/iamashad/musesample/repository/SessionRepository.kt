package com.iamashad.musesample.repository

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.iamashad.musesample.TAG_MUSE_DB
import com.iamashad.musesample.db.DbProvider
import com.iamashad.musesample.db.entities.SessionEntity
import com.iamashad.musesample.model.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Encrypted Room-backed repository.
 * API kept compatible with previous in-memory version: add, updatePdf, delete, sessions Flow.
 */

object SessionRepository {
    private lateinit var appContext: Context
    private val io = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val sessions: Flow<List<Session>> by lazy {
        DbProvider.get(appContext).sessionDao().observeAll().map { rows ->
            rows.map { it.toDomain() }
        }
    }

    /** Add / upsert a session (keeps your existing call site). */
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

    fun updatePdf(id: Long, pdfPath: String) {
        io.launch {
            try {
                DbProvider.get(appContext).sessionDao().updatePdfPath(id, pdfPath)
                val scheme = runCatching {
                    pdfPath.toUri().scheme ?: "file"
                }.getOrDefault("file")
                Log.i(TAG_MUSE_DB, "session_pdf_update_ok | SID=$id | SCHEME=$scheme")
            } catch (t: Throwable) {
                Log.e(
                    TAG_MUSE_DB,
                    "session_pdf_update_fail | SID=$id | REASON=${t.javaClass.simpleName}: ${t.message}"
                )
            }
        }
    }

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
        pdfPath = pdfPath,
        sex = sex,          // store plain string; UI renders as “S.E.X.: …”
        height = height,
        weight = weight,
        bmi = bmi
    )
}

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
    pdfPath = pdfPath,
    sex = sex,
    height = height,
    weight = weight,
    bmi = bmi
)
