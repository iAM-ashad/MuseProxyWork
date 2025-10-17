package com.iamashad.musesample.model

data class PcgReportMeta(
    val patientName: String,
    val patientId: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val age: String = "",
    val sex: String = "Other",
    val height: String = "",
    val weight: String = "",
    val bmi: String = "",
    val posture: String = "Standing",
    val position: String = "Mitral"
)


