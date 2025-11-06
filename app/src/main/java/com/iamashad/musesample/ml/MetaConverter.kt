package com.iamashad.musesample.ml

import com.iamashad.musesample.model.PcgReportMeta

fun mapPcgMetaToModelMeta(meta: PcgReportMeta): FloatArray {
    // age: try to parse age string into years (fallback 0f)
    val ageNum = meta.age.toFloatOrNull() ?: 0f
    // sex: map common strings -> 0.0 male, 1.0 female (ask ML if they used different encoding)
    val sexVal = when (meta.sex.trim().lowercase()) {
        "female", "f" -> 1f
        "male", "m" -> 0f
        else -> 0f
    }
    val bmiVal = meta.bmi.toFloatOrNull() ?: 0f
    return floatArrayOf(ageNum, sexVal, bmiVal)
}
