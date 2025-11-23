package com.iamashad.musesample.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: Long,                 // System.currentTimeMillis()
    val patientName: String,
    val patientId: String,
    val age: String,

    // UI-friendly display string: "dd MMM yyyy, HH:mm"
    val sessionStartDisplay: String,

    // Robust sort key
    val sessionStartEpochMillis: Long,

    val deviceModel: String,
    val notes: String,
    val posture: String,
    val position: String,

    // This path now points to the SDK-FILTERED file
    val wavPath: String,

    // This new column points to the raw file for the ML model
    val rawWavPath: String,

    val pdfPath: String?,

    // Store plain value (UI renders as “S.E.X.: …”)
    val sex: String,
    val height: String,
    val weight: String,
    val bmi: String
)