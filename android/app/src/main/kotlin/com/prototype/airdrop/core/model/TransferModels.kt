package com.prototype.airdrop.core.model

data class TransferItem(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

data class TransferSession(
    val id: String,
    val targetDevice: NearbyDevice,
    val items: List<TransferItem>,
    val status: TransferStatus,
    val progressPercent: Int,
    val speedText: String,
    val errorMessage: String? = null
)

enum class TransferStatus {
    PAIRING_REQUIRED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELLED
}
