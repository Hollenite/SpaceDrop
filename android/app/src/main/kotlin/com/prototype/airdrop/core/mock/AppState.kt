package com.prototype.airdrop.core.mock

import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem

/**
 * Transient navigation state shared across screens during a transfer flow.
 * Cleared when the user returns to Home.
 */
object AppState {
    var pendingTransferItems: List<TransferItem> = emptyList()
    var selectedDevice: NearbyDevice? = null
    var isFromShareSheet: Boolean = false
    var shareRequestId: Int = 0

    fun clear() {
        pendingTransferItems = emptyList()
        selectedDevice = null
        isFromShareSheet = false
    }
}
