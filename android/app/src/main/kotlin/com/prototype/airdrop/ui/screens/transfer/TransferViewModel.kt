package com.prototype.airdrop.ui.screens.transfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.core.model.TransferSession
import com.prototype.airdrop.core.transfer.TransferEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: TransferEngine = if (AppState.isFromShareSheet) {
        AppDependencies.tcpTransferEngine(application)
    } else {
        AppDependencies.transferEngine
    }

    val session: StateFlow<TransferSession?> = engine.session
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun startTransfer() {
        val device = AppState.selectedDevice ?: return
        val items = AppState.pendingTransferItems
        engine.start(device, items)
    }

    fun cancel() {
        engine.cancel()
    }

    fun reset() {
        engine.reset()
    }
}
