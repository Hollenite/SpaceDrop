package com.prototype.airdrop.core.util

import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.model.TrustState

object FormatUtils {

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null) return ""
        return when {
            bytes < 1_024L -> "${bytes} B"
            bytes < 1_048_576L -> "${bytes / 1_024} KB"
            bytes < 1_073_741_824L -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
            else -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
        }
    }

    fun formatTotalSize(items: List<TransferItem>): String {
        val total = items.mapNotNull { it.sizeBytes }.sum()
        return if (total == 0L) "" else formatFileSize(total)
    }

    fun transferSummary(items: List<TransferItem>): String {
        val count = items.size
        val sizeText = formatTotalSize(items)
        val countText = if (count == 1) "1 file" else "$count files"
        return if (sizeText.isEmpty()) countText else "$countText • $sizeText"
    }

    fun deviceSubtitle(device: NearbyDevice): String {
        val trust = when (device.trustState) {
            TrustState.PAIRED -> "Paired"
            TrustState.NEW_DEVICE -> "New device"
            TrustState.BLOCKED -> "Blocked"
        }
        val proximity = when (device.proximityState) {
            ProximityState.READY_NEARBY -> "Ready nearby"
            ProximityState.NEARBY -> "Pair required"
            ProximityState.WEAK_SIGNAL -> "Weak signal"
            ProximityState.OFFLINE -> "Offline"
        }
        return "$trust • $proximity"
    }

    fun shareHeaderText(items: List<TransferItem>): String {
        return when {
            items.isEmpty() -> "Send files to"
            items.size == 1 -> "Send ${items.first().displayName} to"
            else -> "Send ${items.size} files to"
        }
    }
}
