package com.prototype.airdrop.core.model

data class NearbyDevice(
    val id: String,
    val name: String,
    val trustState: TrustState,
    val proximityState: ProximityState,
    val isReady: Boolean,
    val confidence: Float
)

enum class TrustState {
    PAIRED,
    NEW_DEVICE,
    BLOCKED
}

enum class ProximityState {
    READY_NEARBY,
    NEARBY,
    WEAK_SIGNAL,
    OFFLINE
}
