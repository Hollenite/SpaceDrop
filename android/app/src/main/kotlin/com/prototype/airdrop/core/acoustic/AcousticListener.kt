package com.prototype.airdrop.core.acoustic

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.spacedrop.tier0.fsk.AcousticToken
import com.spacedrop.tier0.fsk.FskDecoder
import com.spacedrop.tier0.fsk.FskProtocol
import com.spacedrop.tier0.fsk.GoertzelDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Always-on (while Home/picker is open) microphone listener that decodes 19 kHz FSK tokens emitted
 * by a nearby SpaceDrop PC and exposes them to discovery fusion as a corroboration signal.
 *
 * Ported from the Tier 0 POC AudioToneListener, keeping the Step 1 hardening: per-session
 * [AudioRecord] ownership (released only by the loop that created it) and job chaining via
 * [cancelAndJoin] so a quick stop/start never lets two capture loops release each other's record.
 * The UNPROCESSED source + [FskDecoder] are the exact, live-verified Tier 0 path.
 *
 * This class NEVER creates a device row; it only answers [matchingDeviceIds].
 */
class AcousticListener(
    context: Context,
    private val sampleRate: Int = FskProtocol.SAMPLE_RATE,
    private val freshnessMs: Long = DEFAULT_FRESHNESS_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : AcousticCorroborationSource {

    private val appContext = context.applicationContext

    private var captureJob: Job? = null

    /** token20 -> last-heard wall-clock ms. Concurrent: written on capture thread, read on fusion. */
    private val recentTokens = ConcurrentHashMap<Int, Long>()

    private val _lastDecode = MutableStateFlow<AcousticDecode?>(null)
    /** Most recent CRC-valid decode, for diagnostics/lab surfaces. */
    val lastDecode: StateFlow<AcousticDecode?> = _lastDecode.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (!AcousticPermissions.hasAll(appContext)) {
            Log.w(TAG, "RECORD_AUDIO not granted; acoustic corroboration disabled")
            return
        }
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

    override fun matchingDeviceIds(candidateDeviceIds: Collection<String>): Set<String> {
        if (candidateDeviceIds.isEmpty()) return emptySet()
        val cutoff = nowMs() - freshnessMs
        val iterator = recentTokens.entries.iterator()
        val fresh = HashSet<Int>()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) iterator.remove() else fresh.add(entry.key)
        }
        if (fresh.isEmpty()) return emptySet()

        val epoch = AcousticToken.epochFromUnixSeconds(nowMs() / 1000)
        val epochs = intArrayOf(epoch - 1, epoch, epoch + 1)
        val matched = HashSet<String>()
        for (id in candidateDeviceIds) {
            for (e in epochs) {
                if (AcousticToken.computeToken20(id, e) in fresh) {
                    matched.add(id)
                    break
                }
            }
        }
        return matched
    }

    private suspend fun runCaptureLoop() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = maxOf(minBuffer, GoertzelDetector.DEFAULT_BLOCK_SIZE * 2)
        val audioSource = resolveAudioSource()

        Log.d(TAG, "Starting acoustic capture fs=$sampleRate buffer=$bufferSize source=$audioSource")

        val record = try {
            AudioRecord(audioSource, sampleRate, channelConfig, encoding, bufferSize)
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord construction failed", e)
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        val buffer = ShortArray(GoertzelDetector.DEFAULT_BLOCK_SIZE)
        val decoder = FskDecoder(sampleRate = sampleRate)
        record.startRecording()

        try {
            while (coroutineContext.isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val frames = decoder.process(buffer, read)
                for (frame in frames) {
                    if (!frame.crcValid) continue
                    recentTokens[frame.token20] = nowMs()
                    _lastDecode.value = AcousticDecode(
                        token20 = frame.token20,
                        tokenHex = AcousticToken.tokenHex(frame.token20),
                        heardAtMs = nowMs()
                    )
                    Log.i(TAG, "Acoustic token decoded ${AcousticToken.tokenHex(frame.token20)}")
                }
            }
        } finally {
            // This loop exclusively owns `record`; release here so a restarting session can never
            // release a record belonging to a different session (Step 1 hardening).
            runCatching { record.stop() }
            record.release()
            Log.d(TAG, "Acoustic capture stopped")
        }
    }

    private fun resolveAudioSource(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }

    companion object {
        private const val TAG = "SpaceDropAcoustic"
        private const val DEFAULT_FRESHNESS_MS = 6_000L
    }
}

/** A recently decoded CRC-valid acoustic token, for diagnostics. */
data class AcousticDecode(
    val token20: Int,
    val tokenHex: String,
    val heardAtMs: Long
)
