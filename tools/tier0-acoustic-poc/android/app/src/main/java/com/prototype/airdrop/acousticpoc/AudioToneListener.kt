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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AudioToneListener(
    private val context: Context,
    private val sampleRate: Int = 44_100,
    private val targetFrequencyProvider: () -> Float,
    private val onMetrics: (ToneMetrics) -> Unit
) {
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    fun start(scope: CoroutineScope) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            return
        }

        captureJob = scope.launch(Dispatchers.Default) {
            runCaptureLoop()
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        releaseRecord()
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

        audioRecord = record
        val buffer = ShortArray(GoertzelDetector.DEFAULT_BLOCK_SIZE)
        var targetHz = targetFrequencyProvider()
        var goertzel = GoertzelDetector(sampleRate, targetHz.toDouble())

        var noiseFloor = 1e-6
        var detectedSinceMs: Long? = null
        var overrunCount = 0
        var blockCount = 0

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

                if (magnitude < noiseFloor * 1.5) {
                    noiseFloor = noiseFloor * 0.995 + magnitude * 0.005
                }

                val snrDb = if (magnitude > 0 && noiseFloor > 0) {
                    20.0 * kotlin.math.log10(magnitude / noiseFloor)
                } else {
                    0.0
                }

                val now = System.currentTimeMillis()
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

                withContext(Dispatchers.Main) {
                    onMetrics(
                        ToneMetrics(
                            targetFrequencyHz = targetHz,
                            magnitude = magnitude,
                            noiseFloor = noiseFloor,
                            snrDb = snrDb,
                            detected = detected,
                            bufferOverruns = overrunCount,
                            sampleRate = sampleRate,
                            audioSourceName = sourceLabel
                        )
                    )
                }
            }
        } finally {
            record.stop()
            releaseRecord()
            Log.d(TAG, "Capture stopped blocks=$blockCount")
        }
    }

    private fun releaseRecord() {
        audioRecord?.release()
        audioRecord = null
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
    }
}

data class ToneMetrics(
    val targetFrequencyHz: Float,
    val magnitude: Double,
    val noiseFloor: Double,
    val snrDb: Double,
    val detected: Boolean,
    val bufferOverruns: Int,
    val sampleRate: Int,
    val audioSourceName: String
)
