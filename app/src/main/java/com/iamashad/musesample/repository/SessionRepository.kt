package com.iamashad.musesample.repository

import android.content.Context
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

    // Expose as Flow so existing collectAsState still works
    val sessions: Flow<List<Session>> by lazy {
        DbProvider.get(appContext).sessionDao().observeAll().map { rows ->
            rows.map { it.toDomain() }
        }
    }

    /** Add / upsert a session (keeps your existing call site). */
    fun add(s: Session) {
        io.launch {
            DbProvider.get(appContext).sessionDao().upsert(s.toEntity())
        }
    }

    /** Update only the PDF path (keeps your existing call site). */
    fun updatePdf(id: Long, pdfPath: String) {
        io.launch {
            DbProvider.get(appContext).sessionDao().updatePdfPath(id, pdfPath)
        }
    }

    /** Delete a session (keeps your existing call site). */
    fun delete(s: Session) {
        io.launch {
            DbProvider.get(appContext).sessionDao().delete(s.toEntity())
        }
    }
}

// ---------- Mappers ----------

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
