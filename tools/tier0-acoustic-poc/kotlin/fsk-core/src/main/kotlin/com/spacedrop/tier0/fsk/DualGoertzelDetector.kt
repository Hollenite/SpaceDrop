package com.spacedrop.tier0.fsk

class DualGoertzelDetector(
    sampleRate: Int,
    private val freq0Hz: Double = FskProtocol.BIT0_HZ,
    private val freq1Hz: Double = FskProtocol.BIT1_HZ,
    blockSize: Int = GoertzelDetector.DEFAULT_BLOCK_SIZE
) {
    private val detector0 = GoertzelDetector(sampleRate, freq0Hz, blockSize)
    private val detector1 = GoertzelDetector(sampleRate, freq1Hz, blockSize)

    data class SymbolVote(val mag0: Double, val mag1: Double, val bit: Int, val valid: Boolean)

    fun vote(samples: ShortArray, count: Int, ratioThreshold: Double = FskProtocol.RATIO_THRESHOLD): SymbolVote {
        val mag0 = detector0.magnitude(samples, count)
        val mag1 = detector1.magnitude(samples, count)
        return voteFromMagnitudes(mag0, mag1, ratioThreshold)
    }

    /** Vote over a centered sub-range [from, from+count) to tolerate small symbol-clock offset. */
    fun vote(samples: ShortArray, from: Int, count: Int, ratioThreshold: Double): SymbolVote {
        val mag0 = detector0.magnitude(samples, from, count)
        val mag1 = detector1.magnitude(samples, from, count)
        return voteFromMagnitudes(mag0, mag1, ratioThreshold)
    }

    fun vote(samples: DoubleArray, count: Int, ratioThreshold: Double = FskProtocol.RATIO_THRESHOLD): SymbolVote {
        val mag0 = detector0.magnitude(samples, count)
        val mag1 = detector1.magnitude(samples, count)
        return voteFromMagnitudes(mag0, mag1, ratioThreshold)
    }

    private fun voteFromMagnitudes(mag0: Double, mag1: Double, ratioThreshold: Double): SymbolVote {
        val maxMag = maxOf(mag0, mag1)
        val minMag = minOf(mag0, mag1)
        if (maxMag <= 0.0) {
            return SymbolVote(mag0, mag1, 0, valid = false)
        }
        val bit = when {
            mag1 > mag0 * ratioThreshold -> 1
            mag0 > mag1 * ratioThreshold -> 0
            else -> if (mag1 >= mag0) 1 else 0
        }
        val valid = maxMag > minMag * 1.05
        return SymbolVote(mag0, mag1, bit, valid)
    }
}
