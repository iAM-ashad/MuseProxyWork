package com.iamashad.musesample.model

data class Session(
    val id: Long = System.currentTimeMillis(),
    val patientName: String,
    val patientId: String,
    val age: String,
    val sex: String,
    val height: String,
    val weight: String,
    val bmi: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val posture: String = "Standing",
    val position: String = "Mitral",
    val wavPath: String,
    val pdfPath: String? = null
)
