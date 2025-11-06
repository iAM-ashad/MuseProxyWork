package com.iamashad.musesample.model

/**
* WindowedFeatures: output of buildWindowsFromWave
* - windows: List of FloatArray each length = C*H*W (C=3,H=64,W=188), channel-first
* - starts: start frame for each window in mel frames
* - totalFrames: total mel time frames across clip
*/

data class WindowedFeatures(
    val windows: List<FloatArray>,
    val starts: List<Int>,
    val totalFrames: Int
)