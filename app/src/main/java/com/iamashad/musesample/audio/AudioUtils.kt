package com.iamashad.musesample.audio

import android.util.Log
import com.iamashad.musesample.utils.TAG_PCG_DEBUG
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Audio utilities (cleaned up):
 * - readWavPcm16 -> FloatArray normalized to [-1..1]
 * - bandpassFilter -> biquad cascade (matches Preprocessor's Biquad behavior)
 * - downsampleWaveform -> envelope-preserving downsample; accepts FloatArray
 */

/** Read 16-bit PCM WAV samples and return a normalized mono FloatArray (-1..1) */
fun readWavPcm16(file: File, numChannels: Int): FloatArray {
    val bytes = file.readBytes()
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
        val chunkSize = readU32LE(bytes, pos + 4)
        pos += 8
        if (chunkId == "data") {
            dataStart = pos
            dataSize = chunkSize.coerceAtMost(bytes.size - pos)
            break
        } else {
            // Skip unknown chunk safely
            val safeSkip = chunkSize.coerceAtMost(bytes.size - pos)
            pos += safeSkip
        }
    }

    Log.d(
        TAG_PCG_DEBUG,
        "WAV data chunk found: dataStart=$dataStart, dataSize=$dataSize, bytes.size=${bytes.size}"
    )

    require(dataStart > 0 && dataSize > 0 && dataStart + dataSize <= bytes.size) {
        "Invalid WAV: No 'data' chunk found or file corrupted."
    }

    val bytesPerSample = 2
    val frameSize = numChannels * bytesPerSample
    val end = (dataStart + dataSize).coerceAtMost(bytes.size)
    val approxFrames = ((end - dataStart) / frameSize).coerceAtLeast(0)

    val out = FloatArray(approxFrames)
    var dst = 0
    var i = dataStart
    while (i + 1 < end && dst < approxFrames) {
        // Read little-endian 16-bit signed sample from first channel only
        val lo = bytes[i].toInt() and 0xFF
        val hi = bytes[i + 1].toInt()
        val sample = ((hi shl 8) or lo).toShort().toInt()
        // normalize to [-1, 1]
        out[dst++] = sample / 32768.0f
        i += frameSize
    }
    if (dst != out.size) {
        // shrink if fewer samples than estimated
        return out.copyOf(dst)
    }
    return out
}

/** Helper: read unsigned 32-bit LE from byte array safely. */
private fun readU32LE(bytes: ByteArray, pos: Int): Int {
    if (pos + 4 > bytes.size) return 0
    return (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 3].toInt() and 0xFF) shl 24)
}

/**
 * Robust bandpass using two-stage biquad cascade (Direct Form 1).
 * - Input/return are normalized floats (-1..1).
 * - Default 20..500 Hz is appropriate for PCG.
 */
fun bandpassFilter(
    samples: FloatArray,
    sampleRate: Int,
    lowHz: Float = 20f,
    highHz: Float = 500f
): FloatArray {
    if (samples.isEmpty()) return samples
    // create two-stage cascade using center+Q derived from band edges
    val bw = max(1f, highHz - lowHz)
    val fc = max(1f, (lowHz + highHz) / 2f)
    val q = (fc / bw).coerceAtLeast(0.5f).toDouble()

    val bq1 = Biquad()
    bq1.setBandpass(sampleRate.toDouble(), fc.toDouble(), q)
    val out1 = FloatArray(samples.size)
    bq1.process(samples, out1, resetBefore = true)

    val bq2 = Biquad()
    bq2.setBandpass(sampleRate.toDouble(), fc.toDouble(), q)
    val out2 = FloatArray(samples.size)
    bq2.process(out1, out2, resetBefore = false)

    return out2
}

/**
 * Envelope-preserving downsampling:
 * - Accepts FloatArray of normalized audio (-1..1).
 * - Emits max/min pair per bucket and scales to ±1000.
 */
fun downsampleWaveform(samples: FloatArray, targetCount: Int): List<Float> {
    if (samples.isEmpty()) return emptyList()
    if (samples.size <= targetCount) {
        val maxAbs = samples.maxOfOrNull { abs(it) } ?: 1f
        val scale = if (maxAbs <= 0f) 1f else 1000f / maxAbs
        return samples.map { it * scale }
    }

    val actualBuckets = max(1, targetCount / 2)
    val n = samples.size
    val step = n.toFloat() / actualBuckets

    val out = ArrayList<Float>(actualBuckets * 2)
    var startF = 0f
    for (b in 0 until actualBuckets) {
        val s = floor(startF).toInt()
        val e = min(n, ceil(startF + step).toInt())
        var maxV = Float.NEGATIVE_INFINITY
        var minV = Float.POSITIVE_INFINITY
        if (s >= e) {
            maxV = 0f; minV = 0f
        } else {
            for (i in s until e) {
                val v = samples[i]
                if (v > maxV) maxV = v
                if (v < minV) minV = v
            }
        }
        out.add(maxV)
        out.add(minV)
        startF += step
    }

    val maxAbs = out.maxOfOrNull { abs(it) }?.coerceAtLeast(1e-9f) ?: 1f
    val scale = 1000f / maxAbs
    return out.map { it * scale }
}

/** Read the sample rate (Hz) from the "fmt " chunk. Falls back to 44100 if missing. */
fun readSampleRate(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // skip RIFF header
    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize = readU32LE(bytes, i + 4)
        if (chunkId == "fmt ") {
            // sampleRate is 4 bytes at offset +12 from start of "fmt " chunk
            val off = i + 12
            if (off + 4 <= bytes.size) {
                return (bytes[off].toInt() and 0xFF) or
                        ((bytes[off + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[off + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[off + 3].toInt() and 0xFF) shl 24)
            }
        }
        i += 8 + chunkSize
    }
    return 44100
}

/** Read the number of channels from the "fmt " chunk. Falls back to mono (1). */
fun readNumChannels(file: File): Int {
    val bytes = file.readBytes()
    var i = 12 // skip RIFF header
    while (i + 8 < bytes.size) {
        val chunkId = String(bytes, i, 4)
        val chunkSize = readU32LE(bytes, i + 4)
        if (chunkId == "fmt ") {
            // nChannels: 2 bytes at offset +10 from start of "fmt " chunk
            val off = i + 10
            if (off + 2 <= bytes.size) {
                val channels =
                    (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
                return channels.coerceAtLeast(1)
            }
        }
        i += 8 + chunkSize
    }
    return 1
}

/** Simple biquad (same approach as Preprocessor) */
private class Biquad {
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun setBandpass(fs: Double, fc: Double, q: Double) {
        val omega = 2.0 * Math.PI * fc / fs
        val alpha = kotlin.math.sin(omega) / (2.0 * q)
        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        val a0t = 1.0 + alpha
        a1 = -2.0 * kotlin.math.cos(omega)
        a2 = 1.0 - alpha
        b0 /= a0t; b1 /= a0t; b2 /= a0t; a1 /= a0t; a2 /= a0t
        resetState()
    }

    private fun resetState() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    fun process(input: FloatArray, out: FloatArray, resetBefore: Boolean = true) {
        if (resetBefore) resetState()
        var n = 0
        for (v in input) {
            val x = v.toDouble()
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[n++] = y.toFloat()
            x2 = x1; x1 = x; y2 = y1; y1 = y
        }
    }
}
