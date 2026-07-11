package com.prototype.airdrop.core.transfer

import android.content.ContentResolver
import android.content.Context
import com.prototype.airdrop.core.connectivity.DeviceIdStore
import com.prototype.airdrop.core.connectivity.DeviceNameStore
import com.prototype.airdrop.core.connectivity.SpaceDropFileSender
import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TransferSession
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.core.model.TrustState
import com.prototype.airdrop.core.security.IdentityKeyStore
import com.prototype.airdrop.core.security.PairedPeerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TcpTransferEngine(context: Context) : TransferEngine {

    private val appContext = context.applicationContext
    private val deviceNameStore = DeviceNameStore(appContext)
    private val identity = IdentityKeyStore(appContext).also { it.loadOrCreate() }
    private val peers = PairedPeerStore(appContext)
    private val fileSender = SpaceDropFileSender(
        DeviceIdStore(appContext),
        deviceNameProvider = { deviceNameStore.getName() },
        identity = identity,
        peers = peers
    )
    private val scope = CoroutineScope(Dispatchers.IO)
    private var transferJob: Job? = null

    private val _session = MutableStateFlow<TransferSession?>(null)
    override val session: StateFlow<TransferSession?> = _session.asStateFlow()

    override fun start(
        device: NearbyDevice,
        items: List<TransferItem>,
        contentResolver: ContentResolver?
    ) {
        transferJob?.cancel()

        val host = device.host
        if (host.isNullOrBlank()) {
            _session.value = failedSession(device, items, "Device has no network address")
            return
        }
        if (device.trustState != TrustState.PAIRED && !peers.isPairedDeviceId(device.id)) {
            _session.value = failedSession(
                device,
                items,
                "Not paired — open Pair phone on the PC and scan its QR"
            )
            return
        }
        if (contentResolver == null) {
            _session.value = failedSession(device, items, "Content resolver unavailable")
            return
        }
        if (items.isEmpty()) {
            _session.value = failedSession(device, items, "No files to send")
            return
        }

        val sessionId = UUID.randomUUID().toString()
        _session.value = TransferSession(
            id = sessionId,
            targetDevice = device,
            items = items,
            status = TransferStatus.SENDING,
            progressPercent = 0,
            speedText = "",
            totalFiles = items.size,
            currentFileIndex = 1,
            currentFileName = items.first().displayName
        )

        transferJob = scope.launch {
            val result = fileSender.sendFiles(
                host = host,
                port = device.port,
                items = items,
                contentResolver = contentResolver,
                onProgress = { update ->
                    _session.value = _session.value?.copy(
                        status = TransferStatus.SENDING,
                        progressPercent = update.progressPercent,
                        speedText = update.speedText,
                        totalFiles = update.fileCount,
                        currentFileIndex = update.fileIndex + 1,
                        currentFileName = update.fileName
                    )
                },
                expectedDeviceId = device.id
            )

            _session.value = if (result.success) {
                _session.value?.copy(
                    targetDevice = device.copy(name = result.pcName ?: device.name),
                    status = TransferStatus.COMPLETED,
                    progressPercent = 100,
                    speedText = ""
                )
            } else {
                _session.value?.copy(
                    status = TransferStatus.FAILED,
                    speedText = "",
                    errorMessage = result.error ?: "Transfer failed"
                )
            }
        }
    }

    override fun cancel() {
        transferJob?.cancel()
        transferJob = null
        _session.value = _session.value?.copy(
            status = TransferStatus.CANCELLED,
            speedText = ""
        )
    }

    override fun reset() {
        transferJob?.cancel()
        transferJob = null
        _session.value = null
    }

    private fun failedSession(
        device: NearbyDevice,
        items: List<TransferItem>,
        message: String
    ): TransferSession = TransferSession(
        id = UUID.randomUUID().toString(),
        targetDevice = device,
        items = items,
        status = TransferStatus.FAILED,
        progressPercent = 0,
        speedText = "",
        errorMessage = message
    )

    companion object {
        val DEFAULT_PORT = SpaceDropHandshakeClient.HANDSHAKE_PORT
    }
}
