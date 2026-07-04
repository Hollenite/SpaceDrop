package com.prototype.airdrop.acousticpoc

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Single-frequency Goertzel magnitude detector.
 * See tools/tier0-acoustic-poc/shared/goertzel-notes.md
 */
class GoertzelDetector(
    private val sampleRate: Int,
    targetFrequencyHz: Double,
    private val blockSize: Int = DEFAULT_BLOCK_SIZE
) {
    private val k = round(0.5 + blockSize * targetFrequencyHz / sampleRate).toInt()
    private val omega = 2.0 * PI * k / blockSize
    private val coeff = 2.0 * cos(omega)

    fun magnitude(samples: ShortArray, count: Int = samples.size): Double {
        val n = minOf(count, samples.size)
        var sPrev = 0.0
        var sPrev2 = 0.0
        for (i in 0 until n) {
            val x = samples[i] / 32768.0
            val s = x + coeff * sPrev - sPrev2
            sPrev2 = sPrev
            sPrev = s
        }
        val real = sPrev - sPrev2 * cos(omega)
        val imag = sPrev2 * sin(omega)
        return sqrt(real * real + imag * imag) / n
    }

    companion object {
        const val DEFAULT_BLOCK_SIZE = 2048
    }
}
