package com.iamashad.musesample.utils

import android.util.Log
import com.iamashad.musesample.model.SegmentLabel
import java.util.Locale
import kotlin.math.max

/**
 * Centralized log tags used across the app.
 * Usage:
 *   Log.d(TAG_PCG_GEN, "message")
 *   Log.i(TAG_MUSE_DB, "db opened")
 */

const val TAG_PCG_GEN = "PCG_GEN"   // High-level PCG report generation flow/timing.
const val TAG_PCG_WAV = "PCG_WAV"   // WAV parsing/PCM extraction specifics.
const val TAG_MUSE_DB = "MUSE_DB"   // Room/SQLCipher database lifecycle and DAO ops.
const val TAG_MUSE_SEC = "MUSE_SEC" // Security & key management (EncryptedSharedPreferences).
const val TAG_PCG_DEBUG = "PCG_DEBUG"
const val TAG_PCG_SEG = "PCG_SEG"
const val TAG_SEG_DEBUG = "PCG_SEG_DEBUG"

/** Return a formatted diagnostic string for a list of SegmentLabel. */
fun dumpSegments(segments: List<SegmentLabel>): String {
    if (segments.isEmpty()) return "Segments: <empty>"

    val labelNames = mapOf(
        0 to "S1",
        1 to "Systole",
        2 to "S2",
        3 to "Diastole"
    )

    val sb = StringBuilder()
    sb.append("Segments: ${segments.size}\n")
    // Per-segment lines
    var totalCovered = 0f
    for ((i, s) in segments.withIndex()) {
        val name = labelNames[s.label] ?: "L${s.label}"
        val dur = max(0f, s.endSec - s.startSec)
        totalCovered += dur
        sb.append(
            String.format(
                Locale.US,
                "%3d: %-8s %7.3fs → %7.3fs  (dur: %5.3fs)\n",
                i + 1,
                name,
                s.startSec,
                s.endSec,
                dur
            )
        )
    }

    // Summary counts per label
    val counts = IntArray(4)
    for (s in segments) if (s.label in 0..3) counts[s.label]++
    sb.append("\nSummary:\n")
    for (lab in 0..3) {
        val name = labelNames[lab] ?: "L$lab"
        sb.append(String.format(Locale.US, "  %-8s: %d\n", name, counts[lab]))
    }
    sb.append(String.format(Locale.US, "\nTotal covered time: %.3fs\n", totalCovered))

    return sb.toString()
}

/** Convenience: log to Logcat with PCG_DEBUG tag and return the string. */
fun logAndDumpSegments(tag: String = TAG_PCG_DEBUG, segments: List<SegmentLabel>): String {
    val s = dumpSegments(segments)
    Log.d(tag, s)
    return s
}