package com.iamashad.musesample.ml

import com.iamashad.musesample.model.SegmentLabel

/*object Postprocessor {
    /**
     * segPerSample: [4][64][188] nested arrays produced per sample by ONNX (single sample).
     * Collapse height by summing logits across the 64 rows for each class,
     * then pick class with largest sum for each of the 188 time frames.
     *
     * Returns: IntArray length 188 with class indices {0..3}.
     */
    fun heightCollapseArgmax(segPerSample: Array<Array<FloatArray>>): IntArray {
        require(segPerSample.isNotEmpty()) { "segPerSample empty" }
        val nClasses = segPerSample.size            // 4
        val H = segPerSample[0].size                // 64
        val W = segPerSample[0][0].size             // 188
        val out = IntArray(W)

        for (t in 0 until W) {
            val sums = FloatArray(nClasses)
            for (c in 0 until nClasses) {
                var s = 0f
                for (h in 0 until H) s += segPerSample[c][h][t]
                sums[c] = s
            }
            var best = 0
            var bestVal = sums[0]
            for (c in 1 until nClasses) if (sums[c] > bestVal) {
                best = c; bestVal = sums[c]
            }
            out[t] = best
        }
        return out
    }

    /**
     * Fuse window-level label arrays into global timeline via voting.
     *
     * @param totalFrames total mel frames in the clip
     * @param starts window start indices (in frames)
     * @param windowLabels list of labels arrays, each length 188
     * @return finalLabels length totalFrames
     */
    fun fuseWindowVotes(
        totalFrames: Int,
        starts: List<Int>,
        windowLabels: List<IntArray>
    ): IntArray {
        val C = 4
        val votes = Array(totalFrames) { IntArray(C) { 0 } }
        for ((k, s) in starts.withIndex()) {
            val labels = windowLabels[k]
            for (j in labels.indices) {
                val t = s + j
                if (t in 0 until totalFrames) votes[t][labels[j]]++
            }
        }
        val finalLabels = IntArray(totalFrames)
        for (t in 0 until totalFrames) {
            var best = 0
            var bestVal = votes[t][0]
            for (c in 1 until C) if (votes[t][c] > bestVal) {
                best = c; bestVal = votes[t][c]
            }
            finalLabels[t] = best
        }
        return finalLabels
    }

    /** Median filter in-place to remove speckles. Kernel should be odd. */
    fun medianFilter(labels: IntArray, kernel: Int = 3) {
        val k = if (kernel % 2 == 1) kernel else kernel + 1
        if (k <= 1) return
        val n = labels.size
        val pad = k / 2
        val copy = labels.copyOf()
        val window = IntArray(k)
        for (i in 0 until n) {
            var p = 0
            for (j in -pad..pad) {
                val idx = (i + j).coerceIn(0, n - 1)
                window[p++] = copy[idx]
            }
            window.sort()
            labels[i] = window[k / 2]
        }
    }

    /** Convert frame labels (frame_ms = 16ms) to list of time segments. */
    fun framesToSegments(labels: IntArray, frameMs: Float = 16f): List<SegmentLabel> {
        val segs = mutableListOf<SegmentLabel>()
        if (labels.isEmpty()) return segs
        var cur = labels[0]
        var s = 0
        for (i in 1 until labels.size) {
            if (labels[i] != cur) {
                segs.add(SegmentLabel(cur, s * frameMs / 1000f, i * frameMs / 1000f))
                cur = labels[i]
                s = i
            }
        }
        segs.add(SegmentLabel(cur, s * frameMs / 1000f, labels.size * frameMs / 1000f))
        return segs
    }
}*/

object Postprocessor {

    /**
     * segPerSample: [4][64][188] nested arrays produced per sample by ONNX (single sample).
     *
     * **Correct implementation matching model docs:**
     *  1) For each (h, t) position, take argmax over classes → a label in {0..3}.
     *  2) For each time frame t, take the majority label over all 64 heights.
     *
     * This yields one label per 16 ms frame, length 188.
     */
    fun heightCollapseArgmax(segPerSample: Array<Array<FloatArray>>): IntArray {
        require(segPerSample.isNotEmpty()) { "segPerSample empty" }

        val nClasses = segPerSample.size            // 4
        val H = segPerSample[0].size                // 64
        val W = segPerSample[0][0].size             // 188

        val out = IntArray(W)

        for (t in 0 until W) {
            // votes[c] = how many mel-bins chose class c at this time frame
            val votes = IntArray(nClasses)

            // 1) argmax over class at each height-bin
            for (h in 0 until H) {
                var bestClass = 0
                var bestVal = segPerSample[0][h][t]

                for (c in 1 until nClasses) {
                    val v = segPerSample[c][h][t]
                    if (v > bestVal) {
                        bestVal = v
                        bestClass = c
                    }
                }
                votes[bestClass]++
            }

            // 2) majority vote over height
            var majorityClass = 0
            var majorityVotes = votes[0]
            for (c in 1 until nClasses) {
                if (votes[c] > majorityVotes) {
                    majorityVotes = votes[c]
                    majorityClass = c
                }
            }
            out[t] = majorityClass
        }

        return out
    }

    /**
     * Fuse window-level label arrays into global timeline via voting.
     *
     * @param totalFrames total mel frames in the clip
     * @param starts window start indices (in frames)
     * @param windowLabels list of labels arrays, each length 188
     * @return finalLabels length totalFrames
     */
    fun fuseWindowVotes(
        totalFrames: Int,
        starts: List<Int>,
        windowLabels: List<IntArray>
    ): IntArray {
        val C = 4
        val votes = Array(totalFrames) { IntArray(C) { 0 } }
        for ((k, s) in starts.withIndex()) {
            val labels = windowLabels[k]
            for (j in labels.indices) {
                val t = s + j
                if (t in 0 until totalFrames) votes[t][labels[j]]++
            }
        }
        val finalLabels = IntArray(totalFrames)
        for (t in 0 until totalFrames) {
            var best = 0
            var bestVal = votes[t][0]
            for (c in 1 until C) if (votes[t][c] > bestVal) {
                best = c; bestVal = votes[t][c]
            }
            finalLabels[t] = best
        }
        return finalLabels
    }

    /** Median filter in-place to remove speckles. Kernel should be odd. */
    fun medianFilter(labels: IntArray, kernel: Int = 3) {
        val k = if (kernel % 2 == 1) kernel else kernel + 1
        if (k <= 1) return
        val n = labels.size
        val pad = k / 2
        val copy = labels.copyOf()
        val window = IntArray(k)
        for (i in 0 until n) {
            var p = 0
            for (j in -pad..pad) {
                val idx = (i + j).coerceIn(0, n - 1)
                window[p++] = copy[idx]
            }
            window.sort()
            labels[i] = window[k / 2]
        }
    }

    /** Convert frame labels (frame_ms = 16ms) to list of time segments. */
    fun framesToSegments(labels: IntArray, frameMs: Float = 16f): List<SegmentLabel> {
        val segs = mutableListOf<SegmentLabel>()
        if (labels.isEmpty()) return segs
        var cur = labels[0]
        var s = 0
        for (i in 1 until labels.size) {
            if (labels[i] != cur) {
                segs.add(SegmentLabel(cur, s * frameMs / 1000f, i * frameMs / 1000f))
                cur = labels[i]
                s = i
            }
        }
        segs.add(SegmentLabel(cur, s * frameMs / 1000f, labels.size * frameMs / 1000f))
        return segs
    }
}

