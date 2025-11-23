package com.iamashad.musesample.model

/** Simple container for WAV decode output. */
data class WavData(
    val sampleRate: Int,
    val samples: FloatArray   // mono, normalized to [-1, 1]
)
