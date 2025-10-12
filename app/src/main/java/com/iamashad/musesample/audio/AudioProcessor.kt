package com.iamashad.musesample.audio

import android.util.Log
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

fun readWavPcm16(wavFile: File, numChannels: Int): List<Float> {
    val bytes = wavFile.readBytes()
    require(bytes.size > 44) { "Invalid or too small WAV file" }

    var pos = 12
    var dataStart = -1
    var dataSize = 0
    while (pos + 8 <= bytes.size) {
        if (pos + 8 > bytes.size) break
        val chunkId = try {
            String(bytes, pos, 4)
        } catch (_: Exception) {
            break
        }
        val chunkSize = if (pos + 8 <= bytes.size) {
            (bytes[pos + 4].toInt() and 0xFF) or
                    ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 7].toInt() and 0xFF) shl 24)
        } else 0

        pos += 8
        if (chunkId == "data") {
            dataStart = pos
            dataSize = chunkSize.coerceAtMost(bytes.size - pos)
            break
        } else {
            // Skip safely within bounds
            val safeSkip = chunkSize.coerceAtMost(bytes.size - pos)
            pos += safeSkip
        }
    }
    // ... (Your chunk parsing loop for 'data' chunk) ...
    Log.d(
        "PCG_DEBUG",
        "WAV data chunk found: dataStart=$dataStart, dataSize=$dataSize, bytes.size=${bytes.size}"
    )

    require(dataStart > 0 && dataSize > 0 && dataStart + dataSize <= bytes.size) {
        "Invalid WAV: No 'data' chunk found or file corrupted."
    }

    val pcm = mutableListOf<Float>()
    var i = dataStart
    val end = (dataStart + dataSize).coerceAtMost(bytes.size)

    val bytesPerSample = 2
    val frameSize = numChannels * bytesPerSample
    Log.d(
        "PCG_DEBUG",
        "PCM extraction: numChannels=$numChannels, frameSize=$frameSize bytes, initial i=$i, end=$end"
    )

    var samplesExtracted = 0
    while (i + bytesPerSample <= end) { // Need at least 2 bytes for a 16-bit sample
        val lo = bytes[i].toInt() and 0xFF
        val hi = bytes[i + 1].toInt()
        val sample = (hi shl 8 or lo)
        pcm.add(sample.toShort().toFloat())
        samplesExtracted++

        i += frameSize // Move 'i' to the start of the NEXT FRAME
    }
    Log.d(
        "PCG_DEBUG",
        "Finished PCM extraction. Total samples extracted: $samplesExtracted. Final pcm.size: ${pcm.size}"
    )
    return pcm
}

// ───────────────────────────────
// Improved band-pass filter 20–500 Hz
// ───────────────────────────────
fun bandpassFilter(
    samples: List<Float>,
    sampleRate: Int,
    lowHz: Float = 20f,
    highHz: Float = 500f
): List<Float> {
    if (samples.isEmpty()) return emptyList()

    val filtered = MutableList(samples.size) { 0f }
    val omegaLow = (2 * Math.PI * lowHz / sampleRate).toFloat()
    val omegaHigh = (2 * Math.PI * highHz / sampleRate).toFloat()

    var yLow: Float
    var yHigh: Float
    var prevLow = 0f
    var prevHigh = samples[0]
    val alphaLow = omegaLow / (omegaLow + 1)
    val alphaHigh = 1 / (omegaHigh + 1)

    for (i in 1 until samples.size) {
        val x = samples[i]
        yHigh = alphaHigh * (prevHigh + x - samples[i - 1])  // high-pass
        yLow = prevLow + alphaLow * (yHigh - prevLow)        // low-pass
        filtered[i] = yLow
        prevLow = yLow
        prevHigh = yHigh
    }

    return filtered
}

fun downsampleWaveform(samples: List<Float>, targetCount: Int): List<Float> {
    if (samples.isEmpty() || samples.size <= targetCount) {
        // Normalize the existing data if it's already small enough
        val maxAbs = samples.maxOfOrNull { abs(it) } ?: 1f
        return samples.map { (it / maxAbs) * 1000f }
    }

    // Two data points per segment (peak max and peak min) to correctly visualize the envelope.
    val actualTarget = targetCount / 2
    val n = samples.size
    val step = n.toFloat() / actualTarget.toFloat() // Width of the sample bucket

    val out = ArrayList<Float>(targetCount)
    var currentStart = 0f

    for (i in 0 until actualTarget) {
        val start = currentStart.toInt()
        val end = min(n, ceil(currentStart + step).toInt())

        var maxVal = Float.NEGATIVE_INFINITY
        var minVal = Float.POSITIVE_INFINITY

        for (j in start until end) {
            val sample = samples[j]
            if (sample > maxVal) maxVal = sample
            if (sample < minVal) minVal = sample
        }

        // Add the max peak and the min peak for the segment
        out.add(maxVal)
        out.add(minVal)

        currentStart += step
    }

    // Final normalization to the desired visual amplitude (e.g., +/- 1000f)
    val maxAbs = out.maxOf { abs(it) }.coerceAtLeast(1f)
    return out.map { (it / maxAbs) * 1000f }
}

fun readSampleRate(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // skip RIFF header

    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize = ((bytes[i + 4].toInt() and 0xFF)) or
                ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                ((bytes[i + 7].toInt() and 0xFF) shl 24)
        if (chunkId == "fmt ") {
            if (i + 16 + 4 <= bytes.size) {
                // read 4 bytes at offset +12 relative to start of fmt chunk
                val rate = (bytes[i + 12].toInt() and 0xFF) or
                        ((bytes[i + 13].toInt() and 0xFF) shl 8) or
                        ((bytes[i + 14].toInt() and 0xFF) shl 16) or
                        ((bytes[i + 15].toInt() and 0xFF) shl 24)
                return rate
            }
        }
        i += 8 + chunkSize
    }
    return 44100 // fallback
}

fun readNumChannels(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // Skip RIFF header

    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize = (bytes[i + 4].toInt() and 0xFF) or
                ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                ((bytes[i + 7].toInt() and 0xFF) shl 24)

        if (chunkId == "fmt ") {
            // nChannels is 2 bytes at offset 10 relative to the chunk ID start (i)
            if (i + 10 + 2 <= bytes.size) {
                val channels = (bytes[i + 10].toInt() and 0xFF) or
                        ((bytes[i + 11].toInt() and 0xFF) shl 8) // Little-endian 2-byte read
                return channels.coerceAtLeast(1)
            }
        }
        i += 8 + chunkSize
    }
    return 1 // Fallback
}
