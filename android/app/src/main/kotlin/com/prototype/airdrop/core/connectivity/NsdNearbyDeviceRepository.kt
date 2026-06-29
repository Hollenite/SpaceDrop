package com.prototype.airdrop.core.connectivity



import android.content.Context

import com.prototype.airdrop.core.discovery.NearbyDeviceRepository

import com.prototype.airdrop.core.model.NearbyDevice

import com.prototype.airdrop.core.model.ProximityState

import com.prototype.airdrop.core.model.TrustState

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow



class NsdNearbyDeviceRepository(context: Context) : NearbyDeviceRepository {



    private val appContext = context.applicationContext

    private val discoverer = NsdServiceDiscoverer(appContext)



    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())

    override val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()



    private val _isLoading = MutableStateFlow(false)

    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()



    private val _errorMessage = MutableStateFlow<String?>(null)

    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()



    override suspend fun refresh() {

        _isLoading.value = true

        _errorMessage.value = null

        try {

            val pcs = discoverer.discoverAllPcs()

            _devices.value = pcs.map { it.toNearbyDevice() }

            if (pcs.isEmpty()) {

                _errorMessage.value = "No SpaceDrop PC found on this network"

            }

        } finally {

            _isLoading.value = false

        }

    }



    override fun findById(deviceId: String): NearbyDevice? =

        _devices.value.find { it.id == deviceId }



    override fun stopDiscovery() {

        discoverer.stopDiscovery()

    }



    private fun DiscoveredPc.toNearbyDevice(): NearbyDevice {

        val displayName = serviceName.substringBefore('.').ifBlank { serviceName }

        return NearbyDevice(

            id = deviceId ?: "pc-$host-$port",

            name = displayName,

            trustState = TrustState.PAIRED,

            proximityState = ProximityState.READY_NEARBY,

            isReady = true,

            confidence = 1f,

            host = host,

            port = port

        )

    }

}

