package com.spacedrop.tier0.fsk

object FskProtocol {
    const val SAMPLE_RATE = 44_100
    const val AMPLITUDE = 0.4
    const val PREAMBLE_HZ = 19_000.0
    const val BIT0_HZ = 18_500.0
    const val BIT1_HZ = 19_500.0
    const val PREAMBLE_MS = 300
    const val SYMBOL_MS = 100
    const val INTER_FRAME_GAP_MS = 500
    const val PAYLOAD_BITS = 24
    const val TOKEN_BITS = 20
    const val CRC_BITS = 4
    const val COOLDOWN_MS = 500
    /** Consecutive triple-bin blocks required before preamble onset lock (~46 ms each). */
    const val PREAMBLE_DEBOUNCE_BLOCKS = 2
    const val SNR_THRESHOLD_DB = 12.0
    const val PREAMBLE_LOCK_SNR_DB = 20.0
    /** Absolute 19 kHz magnitude floor; live preamble ~1e-3, data leakage ~1e-5..1e-4. */
    const val PREAMBLE_MIN_MAGNITUDE = 2.0e-4
    const val PREAMBLE_BIN_RATIO = 1.3
    /**
     * Sub-block preamble->data transition detection (symbol-clock alignment).
     * After coarse preamble lock, a short sliding window finds the exact onset of data symbols so
     * every symbol window is centered regardless of where the frame lands on the block grid.
     */
    const val TRANSITION_WINDOW = 1024
    const val TRANSITION_HOP = 128
    const val TRANSITION_RATIO = 1.2
    /**
     * How strongly data bins must exceed the 19 kHz bin for a block to *disarm* the gap gate. A real
     * data symbol shows data bins 20-100x the preamble bin; a gap->preamble straddle block (a short
     * preamble fragment whose narrow Goertzel window smears energy across all bins) sits near 1x and
     * must NOT disarm, or a preamble onset landing late in a block could never lock.
     */
    const val DISARM_DATA_RATIO = 3.0
    const val GAP_QUIET_SNR_DB = 8.0
    /**
     * Absolute 19 kHz magnitude below which a block counts as quiet, regardless of SNR.
     * Guards gap arming against a stale/low noise floor: a decoder created mid-transmission
     * never sees leading silence, so its noise floor stays at its 1e-6 seed and every faint
     * ambient gap reads as high-SNR. Sits below data leakage (~1e-4) and preamble (>=2e-4).
     */
    const val GAP_QUIET_MAGNITUDE = 5.0e-5
    const val GAP_QUIET_MS = 200
    const val RATIO_THRESHOLD = 1.2
    const val EPOCH_SECONDS = 30

    val FRAME_ACTIVE_MS: Int = PREAMBLE_MS + PAYLOAD_BITS * SYMBOL_MS

    fun samplesForMs(ms: Int, sampleRate: Int = SAMPLE_RATE): Int =
        sampleRate * ms / 1000

    /** Quiet gap duration rounded up to whole Goertzel blocks (for tests and alignment). */
    fun alignedGapMs(minMs: Int = GAP_QUIET_MS, blockSize: Int = 2048, sampleRate: Int = SAMPLE_RATE): Int {
        val blockMs = (blockSize * 1000L + sampleRate - 1) / sampleRate
        if (blockMs <= 0) return minMs
        val blocks = (minMs + blockMs - 1) / blockMs
        return (blocks * blockMs).toInt()
    }
}
