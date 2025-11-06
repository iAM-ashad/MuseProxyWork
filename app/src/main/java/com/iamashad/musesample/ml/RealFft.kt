package com.iamashad.musesample.ml

import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal real FFT implementation that returns power spectrum (length N/2+1).
 * Implemented using complex FFT iterative Cooley-Tukey.
 */
/** Minimal real FFT power spectrum for n=power-of-two using radix-2 Cooley-Tukey */
class RealFft(private val n: Int) {
    private val cosTable = DoubleArray(n/2)
    private val sinTable = DoubleArray(n/2)
    init {
        for (i in 0 until n/2) {
            cosTable[i] = cos(2.0 * Math.PI * i / n)
            sinTable[i] = sin(2.0 * Math.PI * i / n)
        }
    }

    /** returns power spectrum length n/2+1 (bins 0..n/2) */
    fun powerSpectrum(frame: FloatArray): FloatArray {
        // zero-pad/copy
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) re[i] = if (i < frame.size) frame[i].toDouble() else 0.0
        fft(re, im)
        val len = n/2 + 1
        val out = FloatArray(len)
        for (k in 0 until len) {
            val r = re[k]; val imv = im[k]
            out[k] = (r*r + imv*imv).toFloat()
        }
        return out
    }

    // in-place complex FFT (radix-2)
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        // bit-reverse
        for (i in 1 until n) {
            var bit = n shr 1
            while (j >= bit) { j -= bit; bit = bit shr 1 }
            j += bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Cooley-Tukey
        var len = 2
        while (len <= n) {
            val half = len / 2
            val tableStep = n / len
            for (i in 0 until n step len) {
                var k = 0
                for (j0 in i until i + half) {
                    val l = j0 + half
                    val tpre = re[l]*cosTable[k] + im[l]*sinTable[k]
                    val tpim = -re[l]*sinTable[k] + im[l]*cosTable[k]
                    re[l] = re[j0] - tpre
                    im[l] = im[j0] - tpim
                    re[j0] += tpre
                    im[j0] += tpim
                    k += tableStep
                }
            }
            len *= 2
        }
    }
}