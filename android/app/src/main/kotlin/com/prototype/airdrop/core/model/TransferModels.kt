package com.prototype.airdrop.core.model

import android.net.Uri

data class TransferItem(
    val contentUri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?
)

data class TransferSession(
    val id: String,
    val targetDevice: NearbyDevice,
    val items: List<TransferItem>,
    val status: TransferStatus,
    /** Overall progress across all files (aggregate bytes). */
    val progressPercent: Int,
    val speedText: String,
    val errorMessage: String? = null,
    /** Total files in this batch. */
    val totalFiles: Int = 1,
    /** 1-based index of the file currently being sent. */
    val currentFileIndex: Int = 1,
    /** Name of the file currently being sent, if known. */
    val currentFileName: String? = null
)

enum class TransferStatus {
    PAIRING_REQUIRED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELLED
}
