package com.iamashad.musesample.ml

import android.util.Log
import com.iamashad.musesample.model.SegmentLabel
import com.iamashad.musesample.utils.TAG_SEG_DEBUG
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Diagnostics {

    data class ClassStats(
        val label: Int,
        val name: String,
        val count: Int,
        val totalSec: Float,
        val meanSec: Float,
        val stdSec: Float
    )

    data class BeatCycle(
        val s1Start: Float, val s1End: Float,
        val s2Start: Float, val s2End: Float,
        val systoleSec: Float,
        val diastoleSec: Float
    )

    /** Summarize segments by class */
    fun summarizeSegments(segs: List<SegmentLabel>): List<ClassStats> {
        if (segs.isEmpty()) return emptyList()
        val byClass = mutableMapOf<Int, MutableList<Float>>()
        for (s in segs) {
            val dur = (s.endSec - s.startSec).coerceAtLeast(0f)
            byClass.getOrPut(s.label) { mutableListOf() }.add(dur)
        }
        val names = arrayOf("S1", "Systole", "S2", "Diastole")
        val out = mutableListOf<ClassStats>()
        for ((label, durations) in byClass.entries.sortedBy { it.key }) {
            val count = durations.size
            val total = durations.sum()
            val mean = if (count > 0) total / count else 0f
            val std = if (count > 1) {
                val m = mean
                sqrt(durations.map { (it - m) * (it - m) }.sum() / (count - 1))
            } else 0f
            val name = names.getOrNull(label) ?: "C$label"
            out.add(ClassStats(label, name, count, total, mean, std))
        }
        return out
    }

    /** Dump summary to log*/
    fun logSummary(segs: List<SegmentLabel>) {
        val stats = summarizeSegments(segs)
        Log.d(TAG_SEG_DEBUG, "Class summary: total segments=${segs.size}")
        for (s in stats) {
            Log.d(
                TAG_SEG_DEBUG,
                "${s.name.padEnd(8)} count=${s.count} total=${"%.3f".format(s.totalSec)}s mean=${
                    "%.3f".format(s.meanSec)
                }s std=${"%.3f".format(s.stdSec)}s"
            )
        }
    }

    /**
     * Detect local peaks in the downsampled envelope 'normalized' which is +/- scaled.
     * - window: look for local max inside +/-halfWin samples
     * - minProminence: relative to local noise floor
     * Returns list of sample indices (indices into normalized).
     */
    fun detectPeaks(
        normalized: List<Float>,
        halfWin: Int = 8,
        minProminence: Float = 0.25f
    ): IntArray {
        val n = normalized.size
        if (n == 0) return IntArray(0)
        val peaks = mutableListOf<Int>()
        val absMax = normalized.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1f)
        for (i in 0 until n) {
            val left = max(0, i - halfWin)
            val right = min(n - 1, i + halfWin)
            val v = normalized[i]
            var isMax = true
            for (j in left..right) {
                if (normalized[j] > v) {
                    isMax = false; break
                }
            }
            if (!isMax) continue
            // prominence: peak should be at least minProminence * absMax above the local median
            val localWindow = normalized.subList(left, right + 1)
            val median = localWindow.sorted().let {
                val m = it.size / 2
                if (it.size % 2 == 1) it[m] else (it[m - 1] + it[m]) / 2f
            }
            val prom = (v - median) / absMax
            if (prom >= minProminence) peaks.add(i)
        }
        return peaks.toIntArray()
    }

    /** Map sample index (in normalized) to time in seconds */
    fun indexToSec(idx: Int, samplesPerSec: Float): Float = idx / samplesPerSec

    /**
     * For each detected peak (indexes in normalized), find which segment (by time) contains it.
     * Returns map: label -> list of peak times (sec)
     */
    fun matchPeaksToSegments(
        peaksIdx: IntArray,
        normalized: List<Float>,
        samplesPerSec: Float,
        segments: List<SegmentLabel>
    ): Map<Int, MutableList<Float>> {
        val out = mutableMapOf<Int, MutableList<Float>>()
        if (peaksIdx.isEmpty() || normalized.isEmpty()) return out
        for (pi in peaksIdx) {
            val t = indexToSec(pi, samplesPerSec)
            // find segment containing t
            val seg = segments.find { t >= it.startSec && t < it.endSec }
            val label = seg?.label ?: -1
            out.getOrPut(label) { mutableListOf() }.add(t)
        }
        return out
    }

    /**
     * Build beat cycles: heuristic:
     * - find S1 segments in sequence; for each S1 find the next S2 after the S1 end.
     * - Systole = S2.start - S1.end
     * - Diastole = (next S1.start) - S2.end  (if next S1 exists)
     *
     * Returns list of BeatCycle with durations in seconds. This is best-effort.
     */
    fun findBeatCycles(segments: List<SegmentLabel>): List<BeatCycle> {
        val out = mutableListOf<BeatCycle>()
        if (segments.isEmpty()) return out
        val s1Indices = segments.mapIndexedNotNull { idx, s -> if (s.label == 0) idx else null }
        for (s1i in s1Indices) {
            val s1 = segments[s1i]
            // find next S2 after s1.end
            val s2 = segments.drop(s1i + 1).find { it.label == 2 }
            if (s2 == null) continue
            val systole = (s2.startSec - s1.endSec).coerceAtLeast(0f)
            // next S1 start
            val nextS1 = segments.drop(s1i + 1).find { it.label == 0 }
            val diastole = if (nextS1 != null) (nextS1.startSec - s2.endSec).coerceAtLeast(0f)
            else (segments.last().endSec - s2.endSec).coerceAtLeast(0f)
            out.add(
                BeatCycle(
                    s1Start = s1.startSec, s1End = s1.endSec,
                    s2Start = s2.startSec, s2End = s2.endSec,
                    systoleSec = systole, diastoleSec = diastole
                )
            )
        }
        return out
    }

    /** Log aggregated beat statistics (mean/std) */
    fun logBeatStats(cycles: List<BeatCycle>) {
        if (cycles.isEmpty()) {
            Log.d(TAG_SEG_DEBUG, "No beat cycles detected")
            return
        }
        val systoles = cycles.map { it.systoleSec }
        val diastoles = cycles.map { it.diastoleSec }
        fun stats(list: List<Float>): Triple<Float, Float, Int> {
            val n = list.size
            val mean = list.sum() / n
            val std = if (n > 1) {
                val m = mean
                sqrt(list.map { (it - m) * (it - m) }.sum() / (n - 1))
            } else 0f
            return Triple(mean, std, n)
        }
        val (ms, ss, ns) = stats(systoles)
        val (md, sd, nd) = stats(diastoles)
        Log.d(
            TAG_SEG_DEBUG,
            "Beats: count=${cycles.size}  systole mean=${"%.3f".format(ms)}s std=${"%.3f".format(ss)}s  diastole mean=${
                "%.3f".format(md)
            }s std=${"%.3f".format(sd)}s"
        )
    }

    /**
     * Produce a CSV string listing suspicious peaks that fall into unexpected classes.
     * For example: peak in diastole (label==3) or peak in class != S1/S2.
     * Returns CSV text for quick copy/paste.
     */
    fun findSuspiciousPeaksCsv(
        normalized: List<Float>,
        samplesPerSec: Float,
        segments: List<SegmentLabel>,
        halfWin: Int = 8,
        minProminence: Float = 0.25f
    ): String {
        val peaks = detectPeaks(normalized, halfWin, minProminence)
        val matched = matchPeaksToSegments(peaks, normalized, samplesPerSec, segments)
        // suspect if label==3 (diastole) OR label !in {0,2} (we expect S1/S2)
        val rows = mutableListOf<String>()
        rows.add("peak_idx,peak_sec,label,label_name,peak_value")
        val names = arrayOf("S1", "Systole", "S2", "Diastole")
        val absMax = normalized.maxOfOrNull { kotlin.math.abs(it) } ?: 1f
        for ((label, times) in matched) {
            for (t in times) {
                // convert time back to index for value
                val idx = (t * samplesPerSec).toInt().coerceIn(0, normalized.size - 1)
                val valAt = normalized[idx]
                val name = names.getOrNull(label) ?: "C$label"
                if (label == 3 || (label != 0 && label != 2)) {
                    rows.add("$idx,${"%.3f".format(t)},$label,$name,${"%.3f".format(valAt / absMax)}")
                }
            }
        }
        return rows.joinToString("\n")
    }
}
