package com.iamashad.musesample.model

/** label: 0=S1, 1=systole, 2=S2, 3=diastole */
data class SegmentLabel(
    val label: Int,
    val startSec: Float,
    val endSec: Float
)
