package com.prototype.airdrop.core.mock

import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TransferSession
import com.prototype.airdrop.core.model.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class FakeTransferEngine(private val repository: MockNearbyDeviceRepository) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var transferJob: Job? = null

    private val _session = MutableStateFlow<TransferSession?>(null)
    val session: StateFlow<TransferSession?> = _session.asStateFlow()

    private val speedValues = listOf(
        "2.1 MB/s", "4.8 MB/s", "6.2 MB/s", "8.4 MB/s", "9.1 MB/s",
        "7.3 MB/s", "8.8 MB/s", "9.5 MB/s", "10.2 MB/s", "8.7 MB/s",
        "6.9 MB/s", "9.3 MB/s", "7.7 MB/s", "10.8 MB/s", "8.1 MB/s"
    )

    fun start(device: NearbyDevice, items: List<TransferItem>) {
        transferJob?.cancel()
        val sessionId = UUID.randomUUID().toString()
        val shouldFail = repository.scenario.value == MockScenario.TRANSFER_FAILURE

        _session.value = TransferSession(
            id = sessionId,
            targetDevice = device,
            items = items,
            status = TransferStatus.SENDING,
            progressPercent = 0,
            speedText = speedValues.first()
        )

        transferJob = scope.launch {
            val totalSteps = 50
            val totalDurationMs = Random.nextLong(4000L, 6001L)
            val stepDelayMs = totalDurationMs / totalSteps
            val failStep = Random.nextInt(30, 36) // ~60–70% of 50

            for (step in 1..totalSteps) {
                delay(stepDelayMs)

                val progress = (step * 100) / totalSteps
                val speed = speedValues[step % speedValues.size]

                if (shouldFail && step == failStep) {
                    _session.value = _session.value?.copy(
                        status = TransferStatus.FAILED,
                        progressPercent = progress,
                        speedText = "",
                        errorMessage = "Connection interrupted"
                    )
                    return@launch
                }

                _session.value = _session.value?.copy(
                    progressPercent = progress,
                    speedText = speed
                )
            }

            _session.value = _session.value?.copy(
                status = TransferStatus.COMPLETED,
                progressPercent = 100,
                speedText = ""
            )
        }
    }

    fun cancel() {
        transferJob?.cancel()
        transferJob = null
        _session.value = _session.value?.copy(
            status = TransferStatus.CANCELLED,
            speedText = ""
        )
    }

    fun reset() {
        transferJob?.cancel()
        transferJob = null
        _session.value = null
    }
}
