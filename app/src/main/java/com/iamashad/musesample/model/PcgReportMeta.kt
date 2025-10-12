package com.iamashad.musesample.model

data class PcgReportMeta(
    val patientName: String,
    val patientId: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val age: String = "",
    val sex: String = "S.E.X.: N/A",
    val height: String = "",
    val weight: String = "",
    val bmi: String = "",
    val posture: String = ""
)


