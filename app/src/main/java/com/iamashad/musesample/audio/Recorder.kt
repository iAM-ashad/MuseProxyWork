package com.iamashad.musesample.audio

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

interface Recorder {
    suspend fun startRecording(): File         // returns in-progress file path (for UI)
    suspend fun stopRecording(): File          // final wav file written
    val isRecording: Boolean
}

/** Mock recorder that writes a tiny valid PCM16 WAV (sine + noise).
 *  Replace with TAAL SDK later by implementing the same interface. */
class FakeRecorder(private val context: Context) : Recorder {
    private var recording = false
    private var currentFile: File? = null
    override val isRecording get() = recording

    override suspend fun startRecording(): File {
        // Simulate device warm-up and file creation
        delay(600)
        val f = File(context.filesDir, "pcg_${System.currentTimeMillis()}.wav")
        // Pre-create header; we’ll rewrite it on stop
        FileOutputStream(f).use { it.write(ByteArray(44)) }
        currentFile = f
        recording = true
        return f
    }

    override suspend fun stopRecording(): File {
        // Simulate device stop + actually synthesize audio now (2–4 s)
        delay(500)
        val file = currentFile ?: error("No active file")
        val sr = 16000
        val secs = (2..4).random()
        val total = sr * secs
        val freqBeat = 2.0   // low thump-like beats
        val beatEvery = sr / 2
        val pcm = ShortArray(total)
        for (i in 0 until total) {
            // base noise + low sine burst to mimic S1/S2-ish peaks
            val t = i / sr.toDouble()
            var s = (sin(2 * PI * 120.0 * t) * 1000.0) + (Math.random() - 0.5) * 600.0
            if (i % beatEvery in 0..120) s += 6000.0 * sin(2 * PI * freqBeat * t)
            val clamped = s.coerceIn(-32768.0, 32767.0).toInt().toShort()
            pcm[i] = clamped
        }
        writeWavPcm16(file, sr, 1, pcm)
        recording = false
        return file
    }

    /** Minimal WAV writer (PCM16 LE). */
    private fun writeWavPcm16(outFile: File, sampleRate: Int, channels: Int, data: ShortArray) {
        val byteRate = sampleRate * channels * 2
        val dataSize = data.size * 2
        val totalSize = 36 + dataSize
        FileOutputStream(outFile).use { fos ->
            fun w(s: String) = fos.write(s.toByteArray())
            fun dw(v: Int) {
                fos.write(byteArrayOf(
                    (v and 0xFF).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(),
                    ((v shr 24) and 0xFF).toByte()
                ))
            }
            fun ww(v: Int) {
                fos.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
            }
            // RIFF header
            w("RIFF"); dw(totalSize); w("WAVE")
            // fmt chunk
            w("fmt "); dw(16); ww(1); ww(channels); dw(sampleRate); dw(byteRate); ww(channels*2); ww(16)
            // data chunk
            w("data"); dw(dataSize)
            // samples
            val buf = ByteArray(dataSize)
            var j = 0
            for (s in data) {
                buf[j++] = (s.toInt() and 0xFF).toByte()
                buf[j++] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
            fos.write(buf)
        }
    }
}
