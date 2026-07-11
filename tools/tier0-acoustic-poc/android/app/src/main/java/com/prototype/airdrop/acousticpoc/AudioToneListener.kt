package com.prototype.airdrop.acousticpoc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import com.spacedrop.tier0.fsk.AcousticToken
import com.spacedrop.tier0.fsk.FskDecoder
import com.spacedrop.tier0.fsk.FskFrame
import com.spacedrop.tier0.fsk.FskDecoderState

class AudioToneListener(
    private val context: Context,
    private val sampleRate: Int = 44_100,
    private val targetFrequencyProvider: () -> Float,
    private val onMetrics: (ToneMetrics) -> Unit,
    private val expectedDeviceIdProvider: () -> String = { DEFAULT_FSK_DEVICE_ID },
    private val expectedEpochProvider: () -> Int = { AcousticToken.epochFromUnixSeconds(System.currentTimeMillis() / 1000) },
    private val onFskDecode: ((FskDecodeEvent) -> Unit)? = null,
    private val onFskStatus: ((FskDecodeStatus) -> Unit)? = null
) {
    private var captureJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            return
        }

        // Chain onto the previous job so the old capture loop fully tears down (its finally
        // releases its own AudioRecord) before a new one is created. Prevents overlapping
        // sessions from releasing each other's record on a quick stop/start restart.
        val previous = captureJob
        captureJob = scope.launch(Dispatchers.Default) {
            previous?.cancelAndJoin()
            runCaptureLoop()
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
    }

    private suspend fun runCaptureLoop() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = maxOf(minBuffer, GoertzelDetector.DEFAULT_BLOCK_SIZE * 2)

        val audioSource = resolveAudioSource()
        val sourceLabel = audioSourceName(audioSource)
        Log.d(TAG, "Starting capture fs=$sampleRate buffer=$bufferSize source=$sourceLabel")

        val record = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            encoding,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        val buffer = ShortArray(GoertzelDetector.DEFAULT_BLOCK_SIZE)
        var targetHz = targetFrequencyProvider()
        var goertzel = GoertzelDetector(sampleRate, targetHz.toDouble())
        val fskDecoder = FskDecoder(sampleRate = sampleRate)
        var lastFskState = FskDecoderState.Idle
        var lastCrcRejectCount = 0

        var noiseFloor = 1e-6
        var detectedSinceMs: Long? = null
        var overrunCount = 0
        var blockCount = 0
        var quietSinceMs: Long? = null
        val blockDurationMs = GoertzelDetector.DEFAULT_BLOCK_SIZE * 1000L / sampleRate

        record.startRecording()

        try {
            while (coroutineContext.isActive) {
                val newTargetHz = targetFrequencyProvider()
                if (newTargetHz != targetHz) {
                    targetHz = newTargetHz
                    goertzel = GoertzelDetector(sampleRate, targetHz.toDouble())
                    Log.d(TAG, "Target frequency -> ${targetHz.toInt()} Hz")
                }

                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        overrunCount++
                        Log.w(TAG, "AudioRecord read error=$read overruns=$overrunCount")
                    }
                    continue
                }

                val magnitude = goertzel.magnitude(buffer, read)
                blockCount++
                val now = System.currentTimeMillis()

                if (magnitude < noiseFloor * 1.5) {
                    noiseFloor = noiseFloor * 0.995 + magnitude * 0.005
                }

                if (magnitude < noiseFloor * 1.2) {
                    val quietStart = quietSinceMs ?: now.also { quietSinceMs = it }
                    if (now - quietStart > 1_000L) {
                        noiseFloor = noiseFloor * 0.5 + magnitude * 0.5
                        quietSinceMs = now
                    }
                } else {
                    quietSinceMs = null
                }

                val snrDb = if (magnitude > 0 && noiseFloor > 0) {
                    20.0 * kotlin.math.log10(magnitude / noiseFloor)
                } else {
                    0.0
                }

                val detecting = snrDb >= DETECTION_SNR_DB
                val detected = if (detecting) {
                    val since = detectedSinceMs ?: now.also { detectedSinceMs = it }
                    now - since >= DETECTION_HOLD_MS
                } else {
                    detectedSinceMs = null
                    false
                }

                if (blockCount % 10 == 0) {
                    Log.d(
                        TAG,
                        "freq=${targetHz.toInt()} mag=${"%.2e".format(magnitude)} " +
                            "snr=${"%.1f".format(snrDb)} dB detected=$detected overruns=$overrunCount"
                    )
                }

                onMetrics(
                    ToneMetrics(
                        targetFrequencyHz = targetHz,
                        magnitude = magnitude,
                        noiseFloor = noiseFloor,
                        snrDb = snrDb,
                        detected = detected,
                        bufferOverruns = overrunCount,
                        sampleRate = sampleRate,
                        audioSourceName = sourceLabel,
                        blockDurationMs = blockDurationMs
                    )
                )

                if (onFskDecode != null || onFskStatus != null) {
                    val crcBefore = fskDecoder.crcRejectCount
                    val frames = fskDecoder.process(buffer, read)
                    for (frame in frames) {
                        onFskDecode?.let { emitFskFrame(frame, it) }
                    }
                    if (fskDecoder.crcRejectCount > crcBefore) {
                        val expected = AcousticToken.tokenHex(
                            AcousticToken.computeToken20(
                                expectedDeviceIdProvider(),
                                expectedEpochProvider()
                            )
                        )
                        Log.w(
                            TAG,
                            "FSK crc-reject got=${fskDecoder.lastRejectedTokenHex} expected=$expected " +
                                "lockSnr=${"%.1f".format(fskDecoder.lastLockSnrDb)} dB"
                        )
                    }
                    val state = fskDecoder.currentState
                    val crcRejects = fskDecoder.crcRejectCount
                    if (onFskStatus != null && (state != lastFskState || crcRejects != lastCrcRejectCount)) {
                        lastFskState = state
                        lastCrcRejectCount = crcRejects
                        onFskStatus(
                            FskDecodeStatus(
                                decoderState = state.name,
                                crcRejectCount = crcRejects,
                                lastRejectedTokenHex = fskDecoder.lastRejectedTokenHex,
                                lastLockSnrDb = fskDecoder.lastLockSnrDb
                            )
                        )
                    }
                }
            }
        } finally {
            // This loop owns `record` exclusively; release it here so a restarting session
            // can never release a record belonging to a different session.
            runCatching { record.stop() }
            record.release()
            Log.d(TAG, "Capture stopped blocks=$blockCount")
        }
    }

    private fun emitFskFrame(frame: FskFrame, onFskDecode: (FskDecodeEvent) -> Unit) {
        val deviceId = expectedDeviceIdProvider()
        val epoch = expectedEpochProvider()
        val expectedToken = AcousticToken.computeToken20(deviceId, epoch)
        val event = FskDecodeEvent(
            tokenHex = AcousticToken.tokenHex(frame.token20),
            token20 = frame.token20,
            crcValid = frame.crcValid,
            epoch = epoch,
            expectedDeviceId = deviceId,
            tokenMatchesDevice = frame.token20 == expectedToken,
            decoderState = "Frame"
        )
        Log.i(
            TAG,
            "FSK decode token=${event.tokenHex} crc=${event.crcValid} match=${event.tokenMatchesDevice} epoch=$epoch"
        )
        onFskDecode(event)
    }

    private fun resolveAudioSource(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }

    private fun audioSourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "source($source)"
    }

    companion object {
        private const val TAG = "SpaceDropAcousticPoc"
        private const val DETECTION_SNR_DB = 12.0
        private const val DETECTION_HOLD_MS = 200L
        const val DEFAULT_FSK_DEVICE_ID = "550e8400-e29b-41d4-a716-446655440000"
    }
}

data class FskDecodeStatus(
    val decoderState: String,
    val crcRejectCount: Int,
    val lastRejectedTokenHex: String? = null,
    val lastLockSnrDb: Double = 0.0
)

data class FskDecodeEvent(
    val tokenHex: String,
    val token20: Int,
    val crcValid: Boolean,
    val epoch: Int,
    val expectedDeviceId: String,
    val tokenMatchesDevice: Boolean,
    val decoderState: String
)

data class ToneMetrics(
    val targetFrequencyHz: Float,
    val magnitude: Double,
    val noiseFloor: Double,
    val snrDb: Double,
    val detected: Boolean,
    val bufferOverruns: Int,
    val sampleRate: Int,
    val audioSourceName: String,
    /** Approximate ms per Goertzel block (~46 ms @ 44.1 kHz / 2048 samples). */
    val blockDurationMs: Long = 46L
)
