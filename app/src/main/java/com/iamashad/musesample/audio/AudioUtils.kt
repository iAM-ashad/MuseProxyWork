package com.iamashad.musesample.audio

import com.iamashad.musesample.model.WavData
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * Visual downsampling for PCG plots.
 *
 * Goals:
 *  - Preserve the overall beat shape (S1/S2 bursts).
 *  - Make "quiet" parts (systole/diastole baseline) look much flatter,
 *    closer to textbook figures.
 *
 * Steps:
 *  1. De-mean the waveform (remove DC).
 *  2. Linearly resample to [targetCount] points (time-uniform).
 *  3. Global max-abs normalization.
 *  4. Non-linear contrast compression: y = sign(x) * (|x|^γ), γ>1.
 *     This shrinks low-amplitude noise more than peaks.
 *  5. Scale to ±1000 for plotting.
 */
fun downsampleWaveform(
    samples: FloatArray,
    targetCount: Int
): List<Float> {
    if (samples.isEmpty() || targetCount <= 1) return emptyList()

    val n = samples.size

    // 1) Remove DC offset (simple mean subtraction)
    var sum = 0f
    for (v in samples) sum += v
    val mean = sum / n.toFloat()
    val centered = FloatArray(n) { i -> samples[i] - mean }

    // 2) Time-uniform resampling via linear interpolation
    val outCount = min(targetCount, n)
    val resampled = FloatArray(outCount)

    if (outCount == 1) {
        resampled[0] = centered[0]
    } else {
        val step = (n - 1).toFloat() / (outCount - 1).toFloat()
        var pos = 0f
        for (i in 0 until outCount) {
            val idx0 = floor(pos).toInt().coerceIn(0, n - 1)
            val idx1 = min(idx0 + 1, n - 1)
            val t = pos - idx0
            val v0 = centered[idx0]
            val v1 = centered[idx1]
            resampled[i] = v0 * (1f - t) + v1 * t
            pos += step
        }
    }

    // 3) Global max-abs normalization
    var maxAbs = 0f
    for (v in resampled) {
        val a = abs(v)
        if (a > maxAbs) maxAbs = a
    }
    if (maxAbs <= 0f) return List(outCount) { 0f }

    // 4) Contrast compression (γ > 1 flattens low-level noise)
    val gamma = 2.0f   // tweakable; 1.5–3.0 are reasonable ranges
    val out = FloatArray(outCount)
    for (i in 0 until outCount) {
        val norm = resampled[i] / maxAbs   // −1..1
        val mag = abs(norm)
        val compressed = mag.pow(gamma)    // shrinks small magnitudes more
        val signed = if (norm >= 0f) compressed else -compressed
        out[i] = signed * 1000f            // scale for plotting
    }

    // 5) Return as List<Float> for the bitmap pipeline
    return out.toList()
}

/**
 * Minimal 16-bit PCM WAV reader (little-endian).
 *
 * - Supports mono or stereo; stereo is downmixed to mono.
 * - Returns FloatArray in [-1, 1].
 * - Intended for SDK-recorded PCG WAVs.
 */
fun readWavMono16(path: String): WavData {
    val file = File(path)
    require(file.exists()) { "WAV file not found: $path" }

    file.inputStream().buffered().use { input ->
        val header12 = ByteArray(12)
        if (input.read(header12) != 12) {
            error("Invalid WAV header (too short)")
        }

        fun tag(bytes: ByteArray, offset: Int): String =
            bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)

        if (tag(header12, 0) != "RIFF" || tag(header12, 8) != "WAVE") {
            error("Not a RIFF/WAVE file: $path")
        }

        var sampleRate = 0
        var numChannels = 0
        var bitsPerSample = 0
        var dataSize = 0

        val buf4 = ByteArray(4)
        val buf2 = ByteArray(2)

        // --- Chunk loop: look for "fmt " and "data" ---
        while (true) {
            val idBytes = ByteArray(4)
            val readId = input.read(idBytes)
            if (readId < 4) break  // no more chunks

            if (input.read(buf4) != 4) {
                error("Unexpected EOF in chunk header")
            }
            val chunkSize = ByteBuffer.wrap(buf4).order(ByteOrder.LITTLE_ENDIAN).int
            val chunkId = idBytes.toString(Charsets.US_ASCII)

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) error("fmt chunk too short")
                    // audioFormat
                    if (input.read(buf2) != 2) error("EOF in fmt")
                    val audioFormat =
                        ByteBuffer.wrap(buf2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

                    // numChannels
                    if (input.read(buf2) != 2) error("EOF in fmt")
                    numChannels = ByteBuffer.wrap(buf2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

                    // sampleRate
                    if (input.read(buf4) != 4) error("EOF in fmt")
                    sampleRate = ByteBuffer.wrap(buf4).order(ByteOrder.LITTLE_ENDIAN).int

                    // byteRate (4), blockAlign (2), bitsPerSample (2)
                    if (input.read(buf4) != 4) error("EOF in fmt")
                    if (input.read(buf2) != 2) error("EOF in fmt")
                    if (input.read(buf2) != 2) error("EOF in fmt")
                    bitsPerSample =
                        ByteBuffer.wrap(buf2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

                    // Skip any remaining fmt bytes
                    val remaining = chunkSize - 16
                    if (remaining > 0) input.skip(remaining.toLong())

                    if (audioFormat != 1) {
                        error("Unsupported WAV format (only PCM=1 supported, got $audioFormat)")
                    }
                }

                "data" -> {
                    dataSize = chunkSize
                    break // we'll read data payload next
                }

                else -> {
                    // Skip unknown chunk
                    if (chunkSize > 0) input.skip(chunkSize.toLong())
                }
            }
        }

        require(sampleRate > 0 && numChannels > 0 && bitsPerSample > 0 && dataSize > 0) {
            "Incomplete WAV header for $path"
        }
        require(bitsPerSample == 16) {
            "Only 16-bit PCM WAV supported (got $bitsPerSample)"
        }

        val bytesPerSample = bitsPerSample / 8
        val totalSamples = dataSize / bytesPerSample
        val frames = totalSamples / numChannels

        val dataBytes = ByteArray(dataSize)
        var read = 0
        while (read < dataSize) {
            val r = input.read(dataBytes, read, dataSize - read)
            if (r <= 0) break
            read += r
        }
        if (read < dataSize) error("Unexpected EOF reading PCM data")

        val bb = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(frames)

        for (i in 0 until frames) {
            var sumS = 0f
            for (ch in 0 until numChannels) {
                val s = bb.getShort().toInt() // 16-bit signed
                sumS += (s / 32768f)
            }
            out[i] = sumS / numChannels.coerceAtLeast(1)
        }

        return WavData(sampleRate = sampleRate, samples = out)
    }
}
