package com.iamashad.musesample.audio

import android.util.Log
import com.iamashad.musesample.utils.TAG_PCG_DEBUG
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Audio utilities for reading simple PCM16 WAV files, filtering, and downsampling
 * to a waveform suitable for UI rendering and PDF export.
 *
 * Scope & assumptions:
 * - PCM 16-bit little-endian samples.
 * - RIFF/WAVE format with standard "fmt " and "data" chunks.
 * - We parse chunks defensively (skip unknown chunks, stay within bounds).
 * - Multi-channel WAVs are stepped by frame size, effectively sampling one channel.
 *
 * Key steps in the pipeline:
 *  1) [readSampleRate] / [readNumChannels] → find stream metadata from "fmt " chunk.
 *  2) [readWavPcm16] → extract 16-bit PCM samples (as Float).
 *  3) [bandpassFilter] → crude 20–500 Hz band-pass targeting PCG energy.
 *  4) [downsampleWaveform] → envelope-style downsampling (max/min per bucket),
 *     normalized to ±1000 for consistent rendering scale.
 */

/**
 * Parse the WAV file and return PCM16 samples as Floats.
 *
 * @param wavFile The WAV file on disk.
 * @param numChannels Number of interleaved channels in the stream (from [readNumChannels]).
 * @return A list of sample values in the native PCM scale (±32768) as Float.
 *
 * Implementation notes:
 * - Locates the "data" chunk by scanning RIFF chunks.
 * - Reads 16-bit little-endian samples.
 * - Advances by frame size (channels * 2 bytes) so we effectively keep one channel's stream.
 */
fun readWavPcm16(wavFile: File, numChannels: Int): List<Float> {
    val bytes = wavFile.readBytes()
    require(bytes.size > 44) { "Invalid or too small WAV file" }

    var pos = 12 // skip RIFF header
    var dataStart = -1
    var dataSize = 0

    // Locate the "data" chunk (skip anything else safely)
    while (pos + 8 <= bytes.size) {
        val chunkId = try {
            String(bytes, pos, 4)
        } catch (_: Exception) {
            break
        }
        val chunkSize =
            (bytes[pos + 4].toInt() and 0xFF) or
                    ((bytes[pos + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[pos + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 7].toInt() and 0xFF) shl 24)

        pos += 8
        if (chunkId == "data") {
            dataStart = pos
            dataSize = chunkSize.coerceAtMost(bytes.size - pos)
            break
        } else {
            // Skip within bounds if unknown chunk
            val safeSkip = chunkSize.coerceAtMost(bytes.size - pos)
            pos += safeSkip
        }
    }

    Log.d(TAG_PCG_DEBUG, "WAV data chunk found: dataStart=$dataStart, dataSize=$dataSize, bytes.size=${bytes.size}")

    require(dataStart > 0 && dataSize > 0 && dataStart + dataSize <= bytes.size) {
        "Invalid WAV: No 'data' chunk found or file corrupted."
    }

    val pcm = mutableListOf<Float>()
    var i = dataStart
    val end = (dataStart + dataSize).coerceAtMost(bytes.size)

    val bytesPerSample = 2
    val frameSize = numChannels * bytesPerSample

    Log.d(TAG_PCG_DEBUG, "PCM extraction: numChannels=$numChannels, frameSize=$frameSize bytes, initial i=$i, end=$end")

    var samplesExtracted = 0
    // Read one 16-bit sample per frame (effectively mono)
    while (i + bytesPerSample <= end) {
        val lo = bytes[i].toInt() and 0xFF
        val hi = bytes[i + 1].toInt()
        val sample = (hi shl 8) or lo
        pcm.add(sample.toShort().toFloat())
        samplesExtracted++
        i += frameSize
    }

    Log.d(TAG_PCG_DEBUG, "Finished PCM extraction. Total samples extracted: $samplesExtracted. Final pcm.size: ${pcm.size}")
    return pcm
}

/**
 * Very lightweight band-pass in ~20–500 Hz for heart sounds.
 *
 * This is a simple 1-pole high-pass followed by 1-pole low-pass (not a precision filter).
 * Good enough to knock down DC/ultra-low and very high frequency noise for visualization.
 */
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

    // Simple leaky integrators
    val alphaLow = omegaLow / (omegaLow + 1)
    val alphaHigh = 1 / (omegaHigh + 1)

    for (i in 1 until samples.size) {
        val x = samples[i]
        // High-pass via simple differentiator + smoothing
        yHigh = alphaHigh * (prevHigh + x - samples[i - 1])
        // Then low-pass
        yLow = prevLow + alphaLow * (yHigh - prevLow)
        filtered[i] = yLow
        prevLow = yLow
        prevHigh = yHigh
    }
    return filtered
}

/**
 * Downsample a long signal into a compact, envelope-preserving sequence for plotting.
 *
 * Strategy:
 * - Split into buckets (targetCount/2 buckets because we output [max, min] per bucket).
 * - For each bucket, emit the local max and min (preserves spikes).
 * - Normalize end result to ±1000 for consistent UI/PDF scale.
 *
 * If the input is already short, just normalize it.
 */
fun downsampleWaveform(samples: List<Float>, targetCount: Int): List<Float> {
    if (samples.isEmpty() || samples.size <= targetCount) {
        val maxAbs = samples.maxOfOrNull { abs(it) } ?: 1f
        return samples.map { (it / maxAbs) * 1000f }
    }

    val actualTarget = targetCount / 2          // two points (max, min) per bucket
    val n = samples.size
    val step = n.toFloat() / actualTarget       // bucket width in samples

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

        out.add(maxVal)
        out.add(minVal)
        currentStart += step
    }

    val maxAbs = out.maxOf { abs(it) }.coerceAtLeast(1f)
    return out.map { (it / maxAbs) * 1000f }
}

/**
 * Read the sample rate (Hz) from the "fmt " chunk. Falls back to 44100 if missing.
 */
fun readSampleRate(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // skip RIFF header

    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize =
            (bytes[i + 4].toInt() and 0xFF) or
                    ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 7].toInt() and 0xFF) shl 24)

        if (chunkId == "fmt ") {
            if (i + 16 + 4 <= bytes.size) {
                // sampleRate is 4 bytes at offset +12 from start of "fmt " chunk
                return (bytes[i + 12].toInt() and 0xFF) or
                        ((bytes[i + 13].toInt() and 0xFF) shl 8) or
                        ((bytes[i + 14].toInt() and 0xFF) shl 16) or
                        ((bytes[i + 15].toInt() and 0xFF) shl 24)
            }
        }
        i += 8 + chunkSize
    }
    return 44100
}

/**
 * Read the number of channels from the "fmt " chunk. Falls back to mono (1).
 */
fun readNumChannels(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // skip RIFF header

    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize =
            (bytes[i + 4].toInt() and 0xFF) or
                    ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[i + 6].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 7].toInt() and 0xFF) shl 24)

        if (chunkId == "fmt ") {
            // nChannels: 2 bytes at offset +10 from start of "fmt " chunk
            if (i + 12 <= bytes.size) {
                val channels = (bytes[i + 10].toInt() and 0xFF) or
                        ((bytes[i + 11].toInt() and 0xFF) shl 8)
                return channels.coerceAtLeast(1)
            }
        }
        i += 8 + chunkSize
    }
    return 1
}
