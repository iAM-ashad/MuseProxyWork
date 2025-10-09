package com.iamashad.musesample.model

import java.io.File

data class SessionItem(
    val id: String,
    val patientName: String,
    val patientId: String,
    val dateTime: String,
    val deviceModel: String,
    val notes: String,
    val pdfFile: File?,
    val audioFile: File?
)

