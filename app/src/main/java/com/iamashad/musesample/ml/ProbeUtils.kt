package com.iamashad.musesample.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun probeMetaFlags(
    context: Context,
    pcm: FloatArray,
    originalSampleRate: Int,
    metaFromReport: FloatArray? = null
) = withContext(Dispatchers.Default) {
    val pre = Preprocessor.buildWindowsFromWave(pcm, originalSampleRate)
    if (pre.windows.isEmpty()) {
        Log.w("PCG_PROBE", "No windows; abort probe")
        return@withContext
    }

    val configs = listOf(
        Pair(floatArrayOf(0f, 0f, 0f), 0L) to "zeros/0",
        Pair(floatArrayOf(1f, 1f, 1f), 0L) to "ones/0",
        Pair(floatArrayOf(0.5f, 0.5f, 0.5f), 0L) to "half/0",
        Pair(floatArrayOf(1f, 0f, 0f), 0L) to "onehotA/0",
        Pair(floatArrayOf(0f, 1f, 0f), 0L) to "onehotB/0",
        Pair(floatArrayOf(0f, 0f, 1f), 0L) to "onehotC/0",
        Pair(floatArrayOf(0f, 0f, 0f), 1L) to "zeros/1",
        Pair(floatArrayOf(1f, 1f, 1f), 1L) to "ones/1",
        Pair(metaFromReport ?: floatArrayOf(0f, 0f, 0f), 1L) to "reportMeta/1"
    )

    for ((cfg, label) in configs) {
        val metaVec = cfg.first
        val flag = cfg.second
        Log.i("PCG_PROBE", "Probing $label meta=${metaVec.joinToString(",")} flags=$flag")
        val model = SegmentationModel(context)
        try {
            val windowLabels = ArrayList<IntArray>()
            for (w in pre.windows) {
                val out =
                    model.runSegmentationWindow(w, metaDefaults = metaVec, flagsDefault = flag)
                val labels = Postprocessor.heightCollapseArgmax(out.segTuple)
                windowLabels.add(labels)
            }
            val fused = Postprocessor.fuseWindowVotes(pre.totalFrames, pre.starts, windowLabels)
            val counts = IntArray(4)
            for (c in fused) counts[c]++
            Log.i(
                "PCG_PROBE",
                "Result $label => S1=${counts[0]} Sys=${counts[1]} S2=${counts[2]} Dia=${counts[3]}"
            )
        } catch (t: Throwable) {
            Log.e("PCG_PROBE", "Probe $label failed: ${t.message}")
        } finally {
            model.close()
        }
    }
}
