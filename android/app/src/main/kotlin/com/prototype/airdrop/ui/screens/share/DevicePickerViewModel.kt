package com.prototype.airdrop.ui.screens.share

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.core.transfer.TcpTransferEngine
import com.prototype.airdrop.ui.components.AvatarTransferState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicePickerViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedRepository = AppDependencies.fusedNearbyDeviceRepository(application)
    private val transferEngine: TcpTransferEngine = AppDependencies.tcpTransferEngine(application)

    val rankedDevices: StateFlow<List<RankedNearbyDevice>> = fusedRepository.rankedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val devices: StateFlow<List<NearbyDevice>> = fusedRepository.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = fusedRepository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val errorMessage: StateFlow<String?> = fusedRepository.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val session = transferEngine.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private var lastHandledShareRequestId: Int = -1

    val pendingItems: List<TransferItem>
        get() = AppState.pendingTransferItems

    init {
        fusedRepository.startPeriodicRefresh(intervalMs = 2_000L)
        observeSuccessfulTransfer()
    }

    override fun onCleared() {
        super.onCleared()
        fusedRepository.stopPeriodicRefresh()
    }

    private fun observeSuccessfulTransfer() {
        viewModelScope.launch {
            var lastRecordedSessionId: String? = null
            session.collect { current ->
                if (current?.status == TransferStatus.COMPLETED &&
                    current.id != lastRecordedSessionId
                ) {
                    lastRecordedSessionId = current.id
                    fusedRepository.deviceUsageStore.recordSuccessfulTransfer(current.targetDevice.id)
                    fusedRepository.refreshQuick()
                }
            }
        }
    }

    fun prepareForNewShare() {
        transferEngine.reset()
        _activeDeviceId.value = null
    }

    fun onShareSheetOpened(shareRequestId: Int) {
        if (shareRequestId == lastHandledShareRequestId) return
        lastHandledShareRequestId = shareRequestId

        prepareForNewShare()

        viewModelScope.launch {
            when {
                fusedRepository.hasFreshCache() -> {
                    Log.d(TAG, "onShareSheetOpened shareRequestId=$shareRequestId path=refreshQuick freshCache")
                    fusedRepository.refreshQuick()
                }
                fusedRepository.hasCachedDevices() -> {
                    Log.d(TAG, "onShareSheetOpened shareRequestId=$shareRequestId path=refreshQuick staleCache")
                    fusedRepository.refreshQuick()
                }
                else -> {
                    Log.d(TAG, "onShareSheetOpened shareRequestId=$shareRequestId path=refresh cold")
                    fusedRepository.ensureBleContinuousScanIfNeeded()
                    fusedRepository.refresh()
                }
            }
        }
    }

    /** User-initiated refresh (pull-to-refresh, empty-state retry). */
    fun refresh() {
        viewModelScope.launch {
            Log.d(TAG, "refresh manual")
            fusedRepository.ensureBleContinuousScanIfNeeded()
            fusedRepository.refresh()
        }
    }

    fun avatarState(deviceId: String, progressPercent: Int): AvatarTransferState {
        val activeId = _activeDeviceId.value ?: return AvatarTransferState.Idle
        if (activeId != deviceId) return AvatarTransferState.Idle

        return when (session.value?.status) {
            TransferStatus.SENDING -> AvatarTransferState.Sending
            TransferStatus.COMPLETED -> AvatarTransferState.Sent
            TransferStatus.FAILED -> AvatarTransferState.Failed
            TransferStatus.CANCELLED -> AvatarTransferState.Idle
            null -> AvatarTransferState.Connecting
            else -> AvatarTransferState.Idle
        }
    }

    fun isDeviceEnabled(deviceId: String): Boolean {
        val activeId = _activeDeviceId.value
        return activeId == null || activeId == deviceId
    }

    fun startTransfer(device: NearbyDevice) {
        if (_activeDeviceId.value != null) return
        if (pendingItems.isEmpty()) return
        if (pendingItems.first().contentUri == Uri.EMPTY) return

        _activeDeviceId.value = device.id
        AppState.selectedDevice = device
        transferEngine.start(
            device = device,
            items = pendingItems,
            contentResolver = getApplication<Application>().contentResolver
        )
    }

    fun cancelTransfer() {
        transferEngine.cancel()
        _activeDeviceId.value = null
    }

    fun resetTransfer() {
        transferEngine.reset()
        _activeDeviceId.value = null
    }

    fun awaitSentAnimation(onDone: () -> Unit) {
        viewModelScope.launch {
            delay(1_500)
            onDone()
        }
    }

    companion object {
        private const val TAG = "SpaceDropDiscovery"
    }
}
