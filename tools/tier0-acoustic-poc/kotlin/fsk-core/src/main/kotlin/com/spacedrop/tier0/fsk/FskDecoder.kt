package com.spacedrop.tier0.fsk

enum class FskDecoderState {
    Idle,
    Preamble,
    Data,
    Cooldown
}

data class FskFrame(
    val token20: Int,
    val crc4: Int,
    val crcValid: Boolean
)

class FskDecoder(
    private val sampleRate: Int = FskProtocol.SAMPLE_RATE,
    private val blockSize: Int = GoertzelDetector.DEFAULT_BLOCK_SIZE,
    private val ratioThreshold: Double = FskProtocol.RATIO_THRESHOLD
) {
    private val preambleDetector = PreambleDetector(sampleRate, blockSize = blockSize)
    private val dualDetector = DualGoertzelDetector(sampleRate, blockSize = blockSize)
    private val symbolSamples = FskProtocol.samplesForMs(FskProtocol.SYMBOL_MS, sampleRate)
    private val preambleSamples = FskProtocol.samplesForMs(FskProtocol.PREAMBLE_MS, sampleRate)
    private val gapQuietSamples = FskProtocol.samplesForMs(FskProtocol.GAP_QUIET_MS, sampleRate)
    private val cooldownSamples = FskProtocol.samplesForMs(FskProtocol.COOLDOWN_MS, sampleRate)

    // Sub-block transition detection: sliding window over 19 kHz vs 18.5/19.5 kHz to find the exact
    // preamble->data onset, so the symbol clock aligns regardless of block-grid phase.
    private val transitionWindow = FskProtocol.TRANSITION_WINDOW
    private val transitionPreambleBin = GoertzelDetector(sampleRate, FskProtocol.PREAMBLE_HZ, transitionWindow)
    private val transitionBit0Bin = GoertzelDetector(sampleRate, FskProtocol.BIT0_HZ, transitionWindow)
    private val transitionBit1Bin = GoertzelDetector(sampleRate, FskProtocol.BIT1_HZ, transitionWindow)
    private val transitionRing = ShortArray(transitionWindow)
    private val transitionScratch = ShortArray(transitionWindow)
    private var transitionRingPos = 0
    private var transitionFilled = 0
    private var transitionHopCounter = 0
    private var preambleTrackSamples = 0
    private var sawPreambleDominant = false

    private var state = FskDecoderState.Idle
    private var preambleConfirmBlocks = 0
    private var quietSamplesAccum = 0
    private var gapArmed = false
    private var bitIndex = 0
    private var decodedBits = 0
    private var cooldownRemaining = 0
    private val blockBuffer = ShortArray(blockSize)
    private var blockPos = 0
    private val symbolBuffer = ShortArray(symbolSamples)
    private var symbolPos = 0

    var crcRejectCount: Int = 0
        private set

    var lastRejectedTokenHex: String? = null
        private set

    var lastLockSnrDb: Double = 0.0
        private set

    val currentState: FskDecoderState get() = state

    fun reset() {
        state = FskDecoderState.Idle
        preambleConfirmBlocks = 0
        quietSamplesAccum = 0
        gapArmed = false
        bitIndex = 0
        decodedBits = 0
        cooldownRemaining = 0
        blockPos = 0
        symbolPos = 0
        resetTransitionTracking()
        crcRejectCount = 0
        lastRejectedTokenHex = null
        lastLockSnrDb = 0.0
    }

    private fun resetTransitionTracking() {
        transitionRingPos = 0
        transitionFilled = 0
        transitionHopCounter = 0
        preambleTrackSamples = 0
        sawPreambleDominant = false
    }

    fun process(samples: ShortArray, count: Int = samples.size): List<FskFrame> {
        val frames = mutableListOf<FskFrame>()
        for (i in 0 until count) {
            processSample(samples[i])?.let { frames += it }
        }
        return frames
    }

    private fun processSample(sample: Short): FskFrame? {
        if (cooldownRemaining > 0) {
            cooldownRemaining--
            if (feedScanBlock(sample, allowLock = true)) {
                cooldownRemaining = 0
                return null
            }
            if (cooldownRemaining == 0) {
                state = FskDecoderState.Idle
                preambleConfirmBlocks = 0
                blockPos = 0
                gapArmed = quietSamplesAccum >= gapQuietSamples
            }
            return null
        }

        return when (state) {
            FskDecoderState.Idle -> {
                feedScanBlock(sample, allowLock = true)
                null
            }
            FskDecoderState.Preamble -> processPreambleSample(sample)
            FskDecoderState.Data -> processDataSample(sample)
            FskDecoderState.Cooldown -> null
        }
    }

    /** Feed one sample into the scan buffer; analyze when full. Returns true if preamble lock fired. */
    private fun feedScanBlock(sample: Short, allowLock: Boolean): Boolean {
        blockBuffer[blockPos++] = sample
        if (blockPos < blockSize) return false
        blockPos = 0
        return analyzeScanBlock(allowLock)
    }

    private fun analyzeScanBlock(allowLock: Boolean): Boolean {
        val block = preambleDetector.analyze(blockBuffer)

        val isQuiet = block.snrDb < FskProtocol.GAP_QUIET_SNR_DB ||
            block.magPreamble < FskProtocol.GAP_QUIET_MAGNITUDE
        if (isQuiet) {
            quietSamplesAccum += blockSize
            if (quietSamplesAccum >= gapQuietSamples) {
                gapArmed = true
            }
            preambleConfirmBlocks = 0
            return false
        }

        quietSamplesAccum = 0
        if (block.isPreamble && gapArmed && allowLock) {
            preambleConfirmBlocks++
            if (preambleConfirmBlocks >= FskProtocol.PREAMBLE_DEBOUNCE_BLOCKS) {
                enterPreamble(block.snrDb, preambleConfirmBlocks)
                return true
            }
        } else {
            preambleConfirmBlocks = 0
            // Disarm only on loud data symbols (data bins dominant), per the gap-gate rule. An
            // ambiguous gap->preamble straddle block (partial 19 kHz, low magnitude) must NOT disarm,
            // otherwise a frame whose preamble onset lands late in a block can never lock.
            val dataMag = maxOf(block.magBit0, block.magBit1)
            val loudData = dataMag > block.magPreamble * FskProtocol.DISARM_DATA_RATIO &&
                dataMag >= FskProtocol.PREAMBLE_MIN_MAGNITUDE
            if (loudData) {
                gapArmed = false
            }
        }
        return false
    }

    private fun enterPreamble(snrDb: Double, confirmedBlocks: Int) {
        state = FskDecoderState.Preamble
        lastLockSnrDb = snrDb
        bitIndex = 0
        decodedBits = 0
        symbolPos = 0
        blockPos = 0
        gapArmed = false
        preambleConfirmBlocks = 0
        quietSamplesAccum = 0
        resetTransitionTracking()
    }

    /**
     * While locked on the preamble, slide a short window to find the exact preamble->data onset.
     * Aligning the symbol clock to that onset (rather than a block-granular estimate) keeps every
     * symbol window centered and eliminates the bit-slip that produced live CRC rejects.
     */
    private fun processPreambleSample(sample: Short): FskFrame? {
        transitionRing[transitionRingPos] = sample
        transitionRingPos = (transitionRingPos + 1) % transitionWindow
        if (transitionFilled < transitionWindow) transitionFilled++
        preambleTrackSamples++

        if (++transitionHopCounter < FskProtocol.TRANSITION_HOP) return null
        transitionHopCounter = 0
        if (transitionFilled < transitionWindow) return null

        linearizeTransitionRing()
        val magPreamble = transitionPreambleBin.magnitude(transitionScratch)
        val magData = maxOf(
            transitionBit0Bin.magnitude(transitionScratch),
            transitionBit1Bin.magnitude(transitionScratch)
        )
        if (magPreamble > magData * FskProtocol.TRANSITION_RATIO) {
            sawPreambleDominant = true
        } else if (sawPreambleDominant && magData > magPreamble * FskProtocol.TRANSITION_RATIO) {
            beginDataFromOnset()
            return null
        }

        // Safety valve: if the transition never appears (garbled/short frame), abandon the lock.
        if (preambleTrackSamples > preambleSamples * 2) {
            state = FskDecoderState.Idle
            blockPos = 0
            resetTransitionTracking()
        }
        return null
    }

    /**
     * Data is dominant now; the true onset is at most one detection window back (detection latency is
     * ~0.55*window + one hop). Seed symbol 0 with the whole buffered window so the symbol clock starts
     * at or slightly before the real onset — never after it (a late clock would run the final symbol
     * past a gap-less frame's end). The centered-core vote discards the leading preamble-tail samples.
     */
    private fun beginDataFromOnset() {
        state = FskDecoderState.Data
        bitIndex = 0
        decodedBits = 0
        symbolPos = 0
        val seed = transitionWindow
        val start = (transitionRingPos - seed + transitionWindow) % transitionWindow
        for (i in 0 until seed) {
            symbolBuffer[symbolPos++] = transitionRing[(start + i) % transitionWindow]
        }
    }

    private fun linearizeTransitionRing() {
        for (i in 0 until transitionWindow) {
            transitionScratch[i] = transitionRing[(transitionRingPos + i) % transitionWindow]
        }
    }

    private fun processDataSample(sample: Short): FskFrame? {
        symbolBuffer[symbolPos++] = sample
        if (symbolPos < symbolSamples) return null
        symbolPos = 0

        // Integrate only the centered core of the symbol so a small residual clock offset (and the
        // symbol's rise/fall edges) cannot flip the bit.
        val guard = symbolSamples / 5
        val vote = dualDetector.vote(symbolBuffer, guard, symbolSamples - 2 * guard, ratioThreshold)
        decodedBits = (decodedBits shl 1) or vote.bit
        bitIndex++
        if (bitIndex < FskProtocol.PAYLOAD_BITS) return null

        val token20 = decodedBits shr FskProtocol.CRC_BITS
        val crc4 = decodedBits and 0xF
        return if (Crc4.verify(token20, crc4)) {
            state = FskDecoderState.Cooldown
            cooldownRemaining = cooldownSamples
            quietSamplesAccum = 0
            blockPos = 0
            FskFrame(token20, crc4, crcValid = true)
        } else {
            crcRejectCount++
            lastRejectedTokenHex = AcousticToken.tokenHex(token20)
            state = FskDecoderState.Idle
            gapArmed = false
            preambleConfirmBlocks = 0
            quietSamplesAccum = 0
            blockPos = 0
            null
        }
    }
}
