package com.prototype.airdrop.core.transfer

import android.content.ContentResolver
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TransferSession
import kotlinx.coroutines.flow.StateFlow

interface TransferEngine {
    val session: StateFlow<TransferSession?>

    fun start(
        device: NearbyDevice,
        items: List<TransferItem>,
        contentResolver: ContentResolver? = null
    )

    fun cancel()
    fun reset()
}
