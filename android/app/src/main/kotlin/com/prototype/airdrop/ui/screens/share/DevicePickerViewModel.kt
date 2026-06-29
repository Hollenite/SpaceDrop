package com.prototype.airdrop.ui.screens.share



import android.app.Application

import android.net.Uri

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import com.prototype.airdrop.core.discovery.FusedNearbyDeviceRepository

import com.prototype.airdrop.core.mock.AppDependencies

import com.prototype.airdrop.core.mock.AppState

import com.prototype.airdrop.core.model.NearbyDevice

import com.prototype.airdrop.core.model.TransferItem

import com.prototype.airdrop.core.model.TransferStatus

import com.prototype.airdrop.core.transfer.TcpTransferEngine

import com.prototype.airdrop.ui.components.AvatarTransferState

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow



class DevicePickerViewModel(application: Application) : AndroidViewModel(application) {



    private val fusedRepository = FusedNearbyDeviceRepository(application)

    private val transferEngine: TcpTransferEngine = AppDependencies.tcpTransferEngine(application)



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



    val pendingItems: List<TransferItem>

        get() = AppState.pendingTransferItems



    val multiFileWarning: Boolean

        get() = pendingItems.size > 1



    init {

        refresh()

    }



    fun refresh() {

        viewModelScope.launch {

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

        if (multiFileWarning) return

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



    override fun onCleared() {

        super.onCleared()

        fusedRepository.stopDiscovery()

    }

}

