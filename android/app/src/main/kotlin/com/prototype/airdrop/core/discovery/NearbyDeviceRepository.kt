package com.prototype.airdrop.core.discovery

import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.StateFlow

interface NearbyDeviceRepository {
    val devices: StateFlow<List<NearbyDevice>>
    val isLoading: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    suspend fun refresh()
    fun stopDiscovery()
    fun findById(deviceId: String): NearbyDevice?
}
