package com.prototype.airdrop.acousticpoc

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.spacedrop.tier0.fsk.AcousticToken

class DetectorViewModel(application: Application) : AndroidViewModel(application) {

    private val _targetFrequencyHz = MutableStateFlow(19_000f)
    val targetFrequencyHz: StateFlow<Float> = _targetFrequencyHz.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _metrics = MutableStateFlow<ToneMetrics?>(null)
    val metrics: StateFlow<ToneMetrics?> = _metrics.asStateFlow()

    private val _rangeTrial = MutableStateFlow(RangeTrialState())
    val rangeTrial: StateFlow<RangeTrialState> = _rangeTrial.asStateFlow()

    private val _burstAutoTest = MutableStateFlow(BurstAutoTestState())
    val burstAutoTest: StateFlow<BurstAutoTestState> = _burstAutoTest.asStateFlow()

    private val _silentTest = MutableStateFlow(SilentTestState())
    val silentTest: StateFlow<SilentTestState> = _silentTest.asStateFlow()

    private val _peakSnr = MutableStateFlow(PeakSnrState())
    val peakSnr: StateFlow<PeakSnrState> = _peakSnr.asStateFlow()

    private val _fskDeviceId = MutableStateFlow(AudioToneListener.DEFAULT_FSK_DEVICE_ID)
    val fskDeviceId: StateFlow<String> = _fskDeviceId.asStateFlow()

    private val _fskEpoch = MutableStateFlow(AcousticToken.epochFromUnixSeconds(System.currentTimeMillis() / 1000))
    val fskEpoch: StateFlow<Int> = _fskEpoch.asStateFlow()

    private val _lastFskDecode = MutableStateFlow<FskDecodeEvent?>(null)
    val lastFskDecode: StateFlow<FskDecodeEvent?> = _lastFskDecode.asStateFlow()

    private val _fskDecodeCount = MutableStateFlow(0)
    val fskDecodeCount: StateFlow<Int> = _fskDecodeCount.asStateFlow()

    private val _fskDecoderState = MutableStateFlow("Idle")
    val fskDecoderState: StateFlow<String> = _fskDecoderState.asStateFlow()

    private val _fskCrcRejectCount = MutableStateFlow(0)
    val fskCrcRejectCount: StateFlow<Int> = _fskCrcRejectCount.asStateFlow()

    private val _fskLastRejectedTokenHex = MutableStateFlow<String?>(null)
    val fskLastRejectedTokenHex: StateFlow<String?> = _fskLastRejectedTokenHex.asStateFlow()

    private val _fskLastLockSnrDb = MutableStateFlow(0.0)
    val fskLastLockSnrDb: StateFlow<Double> = _fskLastLockSnrDb.asStateFlow()

    val expectedTokenHex: StateFlow<String> = combine(_fskDeviceId, _fskEpoch) { deviceId, epoch ->
        AcousticToken.tokenHex(AcousticToken.computeToken20(deviceId, epoch))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AcousticToken.tokenHex(
            AcousticToken.computeToken20(
                AudioToneListener.DEFAULT_FSK_DEVICE_ID,
                AcousticToken.epochFromUnixSeconds(System.currentTimeMillis() / 1000)
            )
        )
    )

    private var silentTestJob: Job? = null
    private var burstAutoTestJob: Job? = null
    private var previousDetected = false

    private var silentTestDetectedSinceMs: Long? = null
    private var silentTestFpCountedForEpisode = false

    private var burstAutoTestStartMs = 0L
    private var burstAutoLoudMsAccum = 0L
    /** Wall-clock gap after each hit (~2 s OFF + margin); avoids stuck cooldown when SNR stays 12–18 dB. */
    private var burstAutoCooldownUntilMs = 0L

    private val listener = AudioToneListener(
        context = application,
        targetFrequencyProvider = { _targetFrequencyHz.value },
        onMetrics = { metrics ->
            _metrics.value = metrics
            processMetrics(metrics)
        },
        expectedDeviceIdProvider = { _fskDeviceId.value },
        expectedEpochProvider = { _fskEpoch.value },
        onFskDecode = { event ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _lastFskDecode.value = event
                _fskDecodeCount.value = _fskDecodeCount.value + 1
            }
        },
        onFskStatus = { status ->
            viewModelScope.launch(Dispatchers.Main.immediate) {
                _fskDecoderState.value = status.decoderState
                _fskCrcRejectCount.value = status.crcRejectCount
                _fskLastRejectedTokenHex.value = status.lastRejectedTokenHex
                _fskLastLockSnrDb.value = status.lastLockSnrDb
            }
        }
    )

    fun setTargetFrequency(hz: Float) {
        _targetFrequencyHz.value = hz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
    }

    fun setFskDeviceId(deviceId: String) {
        _fskDeviceId.value = deviceId
    }

    fun setFskEpoch(epoch: Int) {
        _fskEpoch.value = epoch
    }

    fun refreshFskEpochFromClock() {
        _fskEpoch.value = AcousticToken.epochFromUnixSeconds(System.currentTimeMillis() / 1000)
    }

    fun useTestEpoch100() {
        _fskEpoch.value = 100
    }

    fun resetFskDecodeStats() {
        _lastFskDecode.value = null
        _fskDecodeCount.value = 0
        _fskCrcRejectCount.value = 0
        _fskLastRejectedTokenHex.value = null
        _fskLastLockSnrDb.value = 0.0
        _fskDecoderState.value = "Idle"
    }

    fun startListening() {
        if (_isListening.value) return
        _isListening.value = true
        previousDetected = false
        listener.start(viewModelScope)
    }

    fun stopListening() {
        if (!_isListening.value) return
        cancelSilentTest(markFinished = false)
        cancelBurstAutoTest(markFinished = false)
        listener.stop()
        _isListening.value = false
        previousDetected = false
    }

    fun markBurstHit() {
        val m = _metrics.value
        _rangeTrial.value = _rangeTrial.value.copy(
            hits = _rangeTrial.value.hits + 1
        )
        logTrialMark(hit = true, m)
    }

    fun markBurstMiss() {
        val m = _metrics.value
        _rangeTrial.value = _rangeTrial.value.copy(
            misses = _rangeTrial.value.misses + 1
        )
        logTrialMark(hit = false, m)
    }

    fun resetRangeTrial() {
        _rangeTrial.value = RangeTrialState()
        Log.d(TAG, "Range trial reset")
    }

    fun resetPeakSnr() {
        _peakSnr.value = PeakSnrState()
        Log.d(TAG, "Peak SNR reset")
    }

    fun startBurstAutoTest() {
        if (_burstAutoTest.value.active) return
        if (!_isListening.value) {
            Log.w(TAG, "Start listening before burst auto test")
            return
        }
        if (_silentTest.value.active) {
            Log.w(TAG, "Silent test already running")
            return
        }
        cancelBurstAutoTest(markFinished = false)
        resetBurstAutoTracking()
        _burstAutoTest.value = BurstAutoTestState(
            active = true,
            phase = BurstAutoPhase.COUNTDOWN,
            countdownSec = BURST_AUTO_COUNTDOWN_SEC
        )
        Log.d(TAG, "Burst auto test countdown — start PC burst when countdown hits 0")

        burstAutoTestJob = viewModelScope.launch {
            for (sec in BURST_AUTO_COUNTDOWN_SEC downTo 1) {
                _burstAutoTest.value = _burstAutoTest.value.copy(countdownSec = sec)
                delay(1_000)
                if (!isActive) return@launch
            }
            _burstAutoTest.value = _burstAutoTest.value.copy(
                phase = BurstAutoPhase.RUNNING,
                countdownSec = 0,
                elapsedSec = 0
            )
            burstAutoTestStartMs = System.currentTimeMillis()
            Log.d(TAG, "Burst auto test running (${BURST_AUTO_DURATION_SEC}s; SNR≥${BURST_AUTO_SNR_THRESHOLD_DB} for ${BURST_AUTO_HIT_HOLD_MS}ms = hit)")

            for (second in 1..BURST_AUTO_DURATION_SEC) {
                delay(1_000)
                if (!isActive) return@launch
                val currentBurst = (second * 1000L / BURST_CYCLE_MS).toInt()
                    .coerceIn(0, BURST_AUTO_EXPECTED_BURSTS - 1) + 1
                _burstAutoTest.value = _burstAutoTest.value.copy(
                    elapsedSec = second,
                    currentBurst = currentBurst
                )
            }
            finishBurstAutoTest()
        }
    }

    fun cancelBurstAutoTest() {
        cancelBurstAutoTest(markFinished = false)
        _burstAutoTest.value = BurstAutoTestState()
    }

    private fun cancelBurstAutoTest(markFinished: Boolean) {
        burstAutoTestJob?.cancel()
        burstAutoTestJob = null
        if (markFinished) {
            _burstAutoTest.value = _burstAutoTest.value.copy(
                active = false,
                phase = BurstAutoPhase.FINISHED,
                finished = true
            )
        }
        resetBurstAutoTracking()
    }

    private fun resetBurstAutoTracking() {
        burstAutoTestStartMs = 0L
        burstAutoLoudMsAccum = 0L
        burstAutoCooldownUntilMs = 0L
    }

    private fun finishBurstAutoTest() {
        val state = _burstAutoTest.value
        val misses = (BURST_AUTO_EXPECTED_BURSTS - state.hits).coerceAtLeast(0)
        val finalState = state.copy(misses = misses)
        val pass = finalState.hits >= (BURST_AUTO_EXPECTED_BURSTS * 9 / 10)
        Log.d(
            TAG,
            "Burst auto test complete hits=${finalState.hits} misses=$misses " +
                "rate=${"%.0f".format(finalState.ratePercent)}% pass90=$pass " +
                "(SNR≥${BURST_AUTO_SNR_THRESHOLD_DB} for ${BURST_AUTO_HIT_HOLD_MS}ms; paste into RESULTS Step B)"
        )
        _burstAutoTest.value = finalState.copy(
            active = false,
            phase = BurstAutoPhase.FINISHED,
            finished = true
        )
        burstAutoTestJob = null
        resetBurstAutoTracking()
    }

    private fun scoreBurstAutoHit(snrDb: Double) {
        val state = _burstAutoTest.value
        val newHits = state.hits + 1
        burstAutoCooldownUntilMs = System.currentTimeMillis() + BURST_AUTO_COOLDOWN_MS
        burstAutoLoudMsAccum = 0L
        _burstAutoTest.value = state.copy(hits = newHits, currentBurst = newHits)
        Log.d(
            TAG,
            "Burst auto HIT $newHits/$BURST_AUTO_EXPECTED_BURSTS " +
                "snr=${"%.1f".format(snrDb)} dB held ${BURST_AUTO_HIT_HOLD_MS}ms " +
                "cooldown=${BURST_AUTO_COOLDOWN_MS}ms"
        )
    }

    /**
     * Onset-based scoring using accumulated audio-block time (immune to UI thread jitter).
     * Hit = SNR ≥ 18 dB for 500 ms total; brief dips to 12–18 dB do not reset (hysteresis).
     * After a hit, ignore scoring for [BURST_AUTO_COOLDOWN_MS] (matches PC 2 s OFF gap).
     */
    private fun trackBurstAutoTest(metrics: ToneMetrics) {
        if (_burstAutoTest.value.phase != BurstAutoPhase.RUNNING || burstAutoTestStartMs == 0L) return

        val now = System.currentTimeMillis()
        if (now - burstAutoTestStartMs > BURST_AUTO_DURATION_SEC * 1000L) return
        if (now < burstAutoCooldownUntilMs) return

        val snr = metrics.snrDb
        val blockMs = metrics.blockDurationMs
        val loud = snr >= BURST_AUTO_SNR_THRESHOLD_DB
        val quiet = snr < BURST_AUTO_QUIET_SNR_DB

        if (loud) {
            burstAutoLoudMsAccum += blockMs
            if (burstAutoLoudMsAccum >= BURST_AUTO_HIT_HOLD_MS) {
                scoreBurstAutoHit(snr)
            }
        } else if (quiet) {
            burstAutoLoudMsAccum = 0L
        }
        // SNR in [12, 18): hysteresis — keep accumulated loud time
    }

    fun startSilentTest() {
        if (_silentTest.value.active) return
        if (_burstAutoTest.value.active) {
            Log.w(TAG, "Burst auto test already running")
            return
        }
        if (!_isListening.value) {
            Log.w(TAG, "Start listening before silent test")
            return
        }
        cancelSilentTest(markFinished = false)
        _silentTest.value = SilentTestState(active = true, elapsedSec = 0, falsePositives = 0)
        previousDetected = _metrics.value?.detected == true
        silentTestDetectedSinceMs = null
        silentTestFpCountedForEpisode = false
        Log.d(TAG, "Silent test started (60s) — PC must be silent")

        silentTestJob = viewModelScope.launch {
            for (second in 1..SILENT_TEST_DURATION_SEC) {
                delay(1_000)
                if (!isActive) return@launch
                _silentTest.value = _silentTest.value.copy(elapsedSec = second)
            }
            finishSilentTest()
        }
    }

    fun cancelSilentTest() {
        cancelSilentTest(markFinished = false)
        _silentTest.value = SilentTestState()
    }

    private fun cancelSilentTest(markFinished: Boolean) {
        silentTestJob?.cancel()
        silentTestJob = null
        if (markFinished) {
            _silentTest.value = _silentTest.value.copy(active = false, finished = true)
        }
    }

    private fun finishSilentTest() {
        val fp = _silentTest.value.falsePositives
        val pass = fp == 0
        Log.d(
            TAG,
            "Silent test complete falsePositives=$fp pass=$pass (paste into RESULTS Step D)"
        )
        _silentTest.value = _silentTest.value.copy(active = false, finished = true)
        silentTestJob = null
    }

    private fun processMetrics(metrics: ToneMetrics) {
        if (metrics.snrDb > _peakSnr.value.peakSnrDb) {
            _peakSnr.value = PeakSnrState(
                peakSnrDb = metrics.snrDb,
                peakFreqHz = metrics.targetFrequencyHz
            )
        }

        if (_silentTest.value.active) {
            trackSilentTestFalsePositive(metrics)
        }
        if (_burstAutoTest.value.phase == BurstAutoPhase.RUNNING) {
            trackBurstAutoTest(metrics)
        }
        previousDetected = metrics.detected
    }

    private fun trackSilentTestFalsePositive(metrics: ToneMetrics) {
        val now = System.currentTimeMillis()
        if (metrics.detected) {
            val since = silentTestDetectedSinceMs ?: now.also { silentTestDetectedSinceMs = it }
            val heldMs = now - since
            if (!silentTestFpCountedForEpisode && heldMs >= SUSTAINED_DETECT_HOLD_MS) {
                val silent = _silentTest.value
                _silentTest.value = silent.copy(falsePositives = silent.falsePositives + 1)
                silentTestFpCountedForEpisode = true
                Log.w(
                    TAG,
                    "Silent test false positive #${_silentTest.value.falsePositives} " +
                        "held=${heldMs}ms snr=${"%.1f".format(metrics.snrDb)} dB"
                )
            }
        } else {
            silentTestDetectedSinceMs = null
            silentTestFpCountedForEpisode = false
        }
    }

    private fun logTrialMark(hit: Boolean, metrics: ToneMetrics?) {
        val trial = _rangeTrial.value
        val label = if (hit) "HIT" else "MISS"
        Log.d(
            TAG,
            "Range trial $label ${trial.hits}/${trial.total} " +
                "(${"%.0f".format(trial.ratePercent)}%) " +
                "snr=${metrics?.snrDb?.let { "%.1f".format(it) } ?: "n/a"} dB " +
                "freq=${metrics?.targetFrequencyHz?.toInt() ?: _targetFrequencyHz.value.toInt()} Hz"
        )
    }

    override fun onCleared() {
        cancelSilentTest(markFinished = false)
        cancelBurstAutoTest(markFinished = false)
        listener.stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "SpaceDropAcousticPoc"
        const val MIN_FREQ_HZ = 17_000f
        const val MAX_FREQ_HZ = 21_000f
        const val SILENT_TEST_DURATION_SEC = 60
        /** Sustained DETECTED before counting FP or burst HIT (ignores brief flicker). */
        const val SUSTAINED_DETECT_HOLD_MS = 500L
        const val BURST_AUTO_COUNTDOWN_SEC = 3
        const val BURST_AUTO_DURATION_SEC = 60
        const val BURST_AUTO_EXPECTED_BURSTS = 20
        /** Hit when SNR stays at/above this for [BURST_AUTO_HIT_HOLD_MS] (raw SNR, not DETECTED flag). */
        const val BURST_AUTO_SNR_THRESHOLD_DB = 18.0
        const val BURST_AUTO_HIT_HOLD_MS = 500L
        /** Below this SNR = quiet (matches detection threshold). */
        private const val BURST_AUTO_QUIET_SNR_DB = 12.0
        private const val BURST_AUTO_COOLDOWN_MS = 2_200L
        private const val BURST_CYCLE_MS = 3_000L
    }
}

enum class BurstAutoPhase {
    COUNTDOWN,
    RUNNING,
    FINISHED
}

data class RangeTrialState(
    val hits: Int = 0,
    val misses: Int = 0
) {
    val total: Int get() = hits + misses
    val ratePercent: Double
        get() = if (total > 0) 100.0 * hits / total else 0.0
}

data class BurstAutoTestState(
    val active: Boolean = false,
    val phase: BurstAutoPhase = BurstAutoPhase.FINISHED,
    val countdownSec: Int = 0,
    val elapsedSec: Int = 0,
    val currentBurst: Int = 0,
    val hits: Int = 0,
    val misses: Int = 0,
    val expectedBursts: Int = DetectorViewModel.BURST_AUTO_EXPECTED_BURSTS,
    val finished: Boolean = false
) {
    val total: Int get() = hits + misses
    val ratePercent: Double
        get() = if (total > 0) 100.0 * hits / total else 0.0
    val passGate: Boolean get() = finished && hits >= expectedBursts * 9 / 10
}

data class SilentTestState(
    val active: Boolean = false,
    val elapsedSec: Int = 0,
    val falsePositives: Int = 0,
    val finished: Boolean = false
) {
    val pass: Boolean get() = finished && falsePositives == 0
}

data class PeakSnrState(
    val peakSnrDb: Double = Double.NEGATIVE_INFINITY,
    val peakFreqHz: Float = 0f
) {
    val hasPeak: Boolean get() = peakSnrDb.isFinite()
}
