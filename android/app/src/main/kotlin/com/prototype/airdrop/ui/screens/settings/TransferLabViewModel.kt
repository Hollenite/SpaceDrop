package com.prototype.airdrop.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.connectivity.NsdNearbyDeviceRepository
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.core.transfer.TcpTransferEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransferLabViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdRepository = NsdNearbyDeviceRepository(application)
    private val transferEngine: TcpTransferEngine = AppDependencies.tcpTransferEngine(application)

    val devices: StateFlow<List<NearbyDevice>> = nsdRepository.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = nsdRepository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val errorMessage: StateFlow<String?> = nsdRepository.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val session = transferEngine.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    private var selectedFileSize: Long? = null
    private var selectedMime: String? = null

    val hasSelectedFile: Boolean
        get() = selectedFileUri != null && selectedFileUri != Uri.EMPTY

    val selectedFileLabel: String
        get() = if (hasSelectedFile) selectedFileName else "No file selected"

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { nsdRepository.refresh() }
    }

    fun setSelectedFile(uri: Uri, displayName: String, sizeBytes: Long?, mimeType: String?) {
        selectedFileUri = uri
        selectedFileName = displayName
        selectedFileSize = sizeBytes
        selectedMime = mimeType
    }

    fun sendToFirstDevice() {
        val device = devices.value.firstOrNull() ?: return
        val uri = selectedFileUri ?: return
        val item = TransferItem(
            contentUri = uri,
            displayName = selectedFileName.ifBlank { "file" },
            mimeType = selectedMime,
            sizeBytes = selectedFileSize
        )
        transferEngine.start(
            device = device,
            items = listOf(item),
            contentResolver = getApplication<Application>().contentResolver
        )
    }

    fun cancelTransfer() {
        transferEngine.cancel()
    }

    fun resetTransfer() {
        transferEngine.reset()
    }

    val isTransferring: Boolean
        get() = session.value?.status == TransferStatus.SENDING

    val isComplete: Boolean
        get() = session.value?.status == TransferStatus.COMPLETED

    override fun onCleared() {
        super.onCleared()
        nsdRepository.stopDiscovery()
    }
}
