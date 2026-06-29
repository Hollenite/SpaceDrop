package com.prototype.airdrop.core.connectivity

sealed class ConnectionResult {
    data class Success(val pcDeviceName: String) : ConnectionResult()
    data class Failure(val reason: String? = null) : ConnectionResult()
}
