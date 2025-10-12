package com.iamashad.musesample.model

import java.io.File

data class Session(
    val id: Long = System.currentTimeMillis(),
    val patientName: String,
    val patientId: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val wavPath: String,
    val pdfPath: String? = null
)
