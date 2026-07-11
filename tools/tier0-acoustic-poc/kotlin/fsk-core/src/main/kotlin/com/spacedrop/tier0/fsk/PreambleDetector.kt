package com.spacedrop.tier0.fsk

class PreambleDetector(
    sampleRate: Int,
    private val preambleHz: Double = FskProtocol.PREAMBLE_HZ,
    private val bit0Hz: Double = FskProtocol.BIT0_HZ,
    private val bit1Hz: Double = FskProtocol.BIT1_HZ,
    blockSize: Int = GoertzelDetector.DEFAULT_BLOCK_SIZE,
    private val binRatio: Double = FskProtocol.PREAMBLE_BIN_RATIO,
    private var noiseFloor: Double = 1e-6
) {
    private val preambleBin = GoertzelDetector(sampleRate, preambleHz, blockSize)
    private val bit0Bin = GoertzelDetector(sampleRate, bit0Hz, blockSize)
    private val bit1Bin = GoertzelDetector(sampleRate, bit1Hz, blockSize)

    data class BlockResult(
        val isPreamble: Boolean,
        val snrDb: Double,
        val magPreamble: Double,
        val magBit0: Double,
        val magBit1: Double
    )

    fun analyze(samples: ShortArray, count: Int = samples.size): BlockResult {
        val magPreamble = preambleBin.magnitude(samples, count)
        val magBit0 = bit0Bin.magnitude(samples, count)
        val magBit1 = bit1Bin.magnitude(samples, count)
        updateNoiseFloor(magPreamble)
        val snrDb = magnitudeToSnr(magPreamble)
        val isPreamble = snrDb >= FskProtocol.PREAMBLE_LOCK_SNR_DB &&
            magPreamble >= FskProtocol.PREAMBLE_MIN_MAGNITUDE &&
            magPreamble > magBit0 * binRatio &&
            magPreamble > magBit1 * binRatio
        return BlockResult(isPreamble, snrDb, magPreamble, magBit0, magBit1)
    }

    fun isQuiet(samples: ShortArray, count: Int = samples.size): Boolean {
        val magPreamble = preambleBin.magnitude(samples, count)
        updateNoiseFloor(magPreamble)
        return magnitudeToSnr(magPreamble) < FskProtocol.GAP_QUIET_SNR_DB
    }

    private fun updateNoiseFloor(magnitude: Double) {
        if (magnitude < noiseFloor * 1.5) {
            noiseFloor = noiseFloor * 0.995 + magnitude * 0.005
        }
    }

    private fun magnitudeToSnr(magnitude: Double): Double {
        if (magnitude <= 0 || noiseFloor <= 0) return 0.0
        return 20.0 * kotlin.math.log10(magnitude / noiseFloor)
    }
}
