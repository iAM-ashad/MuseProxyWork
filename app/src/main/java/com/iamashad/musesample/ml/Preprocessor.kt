// File: Preprocessor.kt
package com.iamashad.musesample.ml

import android.util.Log
import com.iamashad.musesample.model.WindowedFeatures
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Preprocessor {
    private const val TARGET_SR = 2000
    private const val HOP = 32               // samples at target sr -> 16 ms
    private const val N_FFT = 256
    private const val N_MELS = 64
    private const val FMAX = 1000.0
    private const val WINDOW_LEN = 188
    private const val STRIDE = 32

    /**
     * High-level: produce windowed [1,3,64,188] FloatArray windows ready for ONNX.
     * Input: pcm samples float[] at arbitrary sample rate (mono, -1..1).
     */
    fun buildWindowsFromWave(pcmIn: FloatArray, inputSampleRate: Int): WindowedFeatures {
        // 1) resample to 2000 Hz (simple linear interpolation)
        val pcm = resampleLinear(pcmIn, inputSampleRate, TARGET_SR)

        // 2) generate 3 band signals using approximate SincConv via 3 bandpass biquads
        // Center / band choices chosen to cover cardiac band energy up to 1000Hz
        val bands = arrayOf(
            bandpassFilter(pcm, TARGET_SR, 20.0, 150.0),
            bandpassFilter(pcm, TARGET_SR, 100.0, 400.0),
            bandpassFilter(pcm, TARGET_SR, 350.0, 1000.0)
        )

        // 3) Compute mel spectrogram per band: [C=3][H=64][T]
        val melSpecPerBand = Array(3) { band ->
            melSpectrogram(
                bands[band],
                TARGET_SR,
                nFft = N_FFT,
                hop = HOP,
                nMels = N_MELS,
                fmax = FMAX
            )
        }

        // 4) Convert power -> dB (10 * log10) and stack, but do normalization per-channel across full clip
        val c = 3
        val h = N_MELS
        val t = melSpecPerBand[0][0].size
        val feat = Array(c) { ch -> Array(h) { FloatArray(t) } }
        for (ch in 0 until c) {
            for (i in 0 until h) for (j in 0 until t) {
                val power = melSpecPerBand[ch][i][j].coerceAtLeast(1e-12f)
                feat[ch][i][j] = (10f * kotlin.math.log10(power.toDouble())).toFloat()
            }
        }

        // 5) Per-channel normalization across HxT (mean/std)
        for (ch in 0 until c) {
            var sum = 0.0;
            var sumsq = 0.0
            val N = h * t
            for (i in 0 until h) for (j in 0 until t) {
                val v = feat[ch][i][j].toDouble()
                sum += v; sumsq += v * v
            }
            val mean = (sum / N).toFloat()
            val variance = (sumsq / N - mean * mean).toFloat().coerceAtLeast(1e-12f)
            val std = sqrt(variance.toDouble()).toFloat()
            for (i in 0 until h) for (j in 0 until t) {
                feat[ch][i][j] = (feat[ch][i][j] - mean) / (std + 1e-6f)
            }
            Log.d("PREPROC", "Ch $ch mean=${mean.format(4)} std=${std.format(4)}")
        }

        // 6) Windowing: cut overlapping windows of width WINDOW_LEN, hop STRIDE
        val starts = mutableListOf<Int>()
        var s = 0
        while (s + WINDOW_LEN <= t) {
            starts.add(s)
            s += STRIDE
        }
        // Option B: include right-aligned tail window if T < WINDOW_LEN or last partial
        if (starts.isEmpty()) starts.add(0) else if (starts.last() + WINDOW_LEN < t) {
            starts.add(max(0, t - WINDOW_LEN))
        }

        // Build windows as flattened FloatArray with layout [C,H,W] channel-major
        val windows = ArrayList<FloatArray>(starts.size)
        for (st in starts) {
            val buf = FloatArray(3 * N_MELS * WINDOW_LEN)
            var off = 0
            for (ch in 0 until c) {
                for (i in 0 until N_MELS) {
                    for (j in 0 until WINDOW_LEN) {
                        val idxT = st + j
                        val v = if (idxT in 0 until t) feat[ch][i][idxT] else 0f
                        buf[off++] = v
                    }
                }
            }
            windows.add(buf)
        }

        return WindowedFeatures(windows = windows, starts = starts, totalFrames = t)
    }

    // ---------- Utilities ----------

    private fun Float.format(d: Int) = "%.${d}f".format(this)

    // Linear resample from srIn -> srOut
    fun resampleLinear(input: FloatArray, srIn: Int, srOut: Int): FloatArray {
        if (srIn == srOut) return input.copyOf()
        val lenIn = input.size
        val duration = lenIn.toDouble() / srIn
        val lenOut = max(1, (duration * srOut).roundToInt())
        val out = FloatArray(lenOut)
        val ratio = (lenIn - 1).toDouble() / (lenOut - 1).toDouble()
        for (i in 0 until lenOut) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val t = (pos - i0).toFloat()
            val v0 = input.getOrElse(i0) { 0f }
            val v1 = input.getOrElse(i0 + 1) { 0f }
            out[i] = v0 * (1 - t) + v1 * t
        }
        return out
    }

    // Simple biquad bandpass filter cascade (2nd-order sections)
    fun bandpassFilter(
        input: FloatArray,
        sr: Int,
        lowHz: Double,
        highHz: Double
    ): FloatArray {
        // design center frequency + Q from bandwidth
        val bw = highHz - lowHz
        val fc = (lowHz + highHz) / 2.0
        val q = if (bw <= 0.0) 1.0 else fc / bw
        val bq = Biquad()
        bq.setBandpass(sr.toDouble(), fc, q)
        val out = FloatArray(input.size)
        var i = 0
        var tmp = FloatArray(input.size)
        bq.process(input, out)
        // second stage to sharpen
        bq.process(out, tmp)
        return tmp
    }

    // Hanning window
    private fun hann(n: Int): FloatArray {
        val w = FloatArray(n)
        for (i in 0 until n) w[i] = (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
        return w
    }

    // Mel filter bank + STFT pipeline
    private fun melSpectrogram(
        wave: FloatArray,
        sr: Int,
        nFft: Int = 256,
        hop: Int = 32,
        nMels: Int = 64,
        fmax: Double = 1000.0
    ): Array<FloatArray> {
        val win = hann(nFft)
        val nFrames = max(1, 1 + ((wave.size - nFft) / hop).coerceAtLeast(0))
        // Prepare FFT helper
        val fft = RealFft(nFft)
        val melFb = melFilterBank(sr, nFft, nMels, fmax)
        // output: [nMels][nFrames]
        val out = Array(nMels) { FloatArray(nFrames) { 0f } }
        val frameBuf = FloatArray(nFft)
        for (fr in 0 until nFrames) {
            val off = fr * hop
            // window
            for (i in 0 until nFft) frameBuf[i] =
                (if (off + i < wave.size) wave[off + i] else 0f) * win[i]
            val magsq = fft.powerSpectrum(frameBuf) // length nFft/2+1
            // mel energies
            for (m in 0 until nMels) {
                var s = 0.0f
                val filt = melFb[m]
                val len = min(filt.size, magsq.size)
                for (k in 0 until len) {
                    s += filt[k] * magsq[k]
                }
                out[m][fr] = s
            }
        }
        return out
    }

    // Build mel filterbank: returns nMels arrays each length (nFft/2+1)
    private fun melFilterBank(sr: Int, nFft: Int, nMels: Int, fmax: Double): Array<FloatArray> {
        val nFreqs = nFft / 2 + 1
        val melToHz = { m: Double -> 700.0 * (exp(m / 1127.0) - 1.0) }
        val hzToMel = { f: Double -> 1127.0 * ln(1.0 + f / 700.0) }
        val fMin = 0.0
        val mMin = hzToMel(fMin)
        val mMax = hzToMel(fmax)
        val mPts = DoubleArray(nMels + 2) { i -> mMin + (mMax - mMin) * i / (nMels + 1) }
        val hzPts = DoubleArray(mPts.size) { i -> melToHz(mPts[i]) }
        val fftFreqs = DoubleArray(nFreqs) { k -> k.toDouble() * sr / nFft.toDouble() }
        val fb = Array(nMels) { FloatArray(nFreqs) { 0f } }
        for (m in 0 until nMels) {
            val fLeft = hzPts[m]
            val fCenter = hzPts[m + 1]
            val fRight = hzPts[m + 2]
            for (k in 0 until nFreqs) {
                val f = fftFreqs[k]
                val w = when {
                    f < fLeft -> 0.0
                    f <= fCenter -> (f - fLeft) / (fCenter - fLeft)
                    f <= fRight -> (fRight - f) / (fRight - fCenter)
                    else -> 0.0
                }
                fb[m][k] = w.toFloat().coerceAtLeast(0f)
            }
        }
        return fb
    }
}

/** Simple second-order biquad filter (Direct Form 1) used for bandpass approximation. */
private class Biquad {
    private var a0 = 1.0;
    private var a1 = 0.0;
    private var a2 = 0.0
    private var b0 = 1.0;
    private var b1 = 0.0;
    private var b2 = 0.0
    private var z1 = 0.0;
    private var z2 = 0.0

    /**
     * Design a band-pass using bilinear transform from analog bandpass prototype:
     * fs: sample rate, fc: center freq, Q: quality factor
     */
    fun setBandpass(fs: Double, fc: Double, q: Double) {
        val omega = 2.0 * Math.PI * fc / fs
        val alpha = sin(omega) / (2.0 * q)
        b0 = alpha
        b1 = 0.0
        b2 = -alpha
        val a0t = 1.0 + alpha
        a1 = -2.0 * cos(omega)
        a2 = 1.0 - alpha
        // normalize to a0 = 1
        a0 = 1.0
        b0 /= a0t; b1 /= a0t; b2 /= a0t; a1 /= a0t; a2 /= a0t
        // reset state
        z1 = 0.0; z2 = 0.0
    }

    fun process(input: FloatArray, out: FloatArray) {
        var n = 0
        for (x in input) {
            val inVal = x.toDouble()
            val y = b0 * inVal + b1 * z1 + b2 * z2 - a1 * z1 - a2 * z2
            out[n++] = y.toFloat()
            // shift
            z2 = z1
            z1 = inVal
        }
    }
}