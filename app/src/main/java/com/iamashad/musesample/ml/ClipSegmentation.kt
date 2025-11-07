package com.iamashad.musesample.ml

import android.content.Context
import android.util.Log
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.SegmentLabel
import com.iamashad.musesample.utils.TAG_PCG_SEG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun runSegmentationOverClip(
    context: Context,
    pcm: FloatArray,
    originalSampleRate: Int,
    datasetFlag: Long = 0L,
    metaFromReport: PcgReportMeta? = null,
    medianKernel: Int = 7,
    inferenceBatchSize: Int = 8   // tune this for device
): List<SegmentLabel> = withContext(Dispatchers.Default) {
    Log.d(TAG_PCG_SEG, "Starting segmentation (pcm=${pcm.size}, sr=$originalSampleRate)")

    // 1) Build windows (Preprocessor returns channel-first FloatArray windows)
    val windowed = Preprocessor.buildWindowsFromWave(pcm, originalSampleRate)
    if (windowed.windows.isEmpty()) return@withContext emptyList()

    // prepare meta vector once (used for all windows)
    val metaVec = if (datasetFlag == 1L && metaFromReport != null) mapPcgMetaToModelMeta(metaFromReport) else floatArrayOf(0f, 0f, 0f)

    val model = SegmentationModel(context)
    val windowLabels = ArrayList<IntArray>(windowed.windows.size)
    try {
        // Run windows in batched groups
        val batchedOutputs = model.runSegmentationWindows(
            windows = windowed.windows,
            metaDefaults = metaVec,
            flagsDefault = datasetFlag,
            batchSize = inferenceBatchSize
        )

        // Convert each SegmentationOutputs.segTuple -> labels188 by height-collapse argmax
        for ((i, out) in batchedOutputs.withIndex()) {
            val labels188 = Postprocessor.heightCollapseArgmax(out.segTuple)
            windowLabels.add(labels188)
            if (i % 20 == 0) Log.d(TAG_PCG_SEG, "Processed window index $i/${batchedOutputs.size}")
        }
    } finally {
        model.close()
    }

    // fuse, smooth, frames->segments (same as before)
    val fused = Postprocessor.fuseWindowVotes(windowed.totalFrames, windowed.starts, windowLabels)
    Postprocessor.medianFilter(fused, medianKernel)
    val segments = Postprocessor.framesToSegments(fused, frameMs = 16f)

    Log.d(TAG_PCG_SEG, "Segmentation complete: ${segments.size} segments.")

    return@withContext segments
}
