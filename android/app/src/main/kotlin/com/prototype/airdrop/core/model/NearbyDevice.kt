package com.prototype.airdrop.core.model

import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient

data class NearbyDevice(
    val id: String,
    val name: String,
    val trustState: TrustState,
    val proximityState: ProximityState,
    val isReady: Boolean,
    val confidence: Float,
    val host: String? = null,
    val port: Int = SpaceDropHandshakeClient.HANDSHAKE_PORT
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
