package com.prototype.airdrop.core.discovery

import android.content.Context
import com.prototype.airdrop.core.connectivity.NsdServiceDiscoverer
import com.prototype.airdrop.core.connectivity.ble.BlePcScanner
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FusedNearbyDeviceRepository(context: Context) : NearbyDeviceRepository {

    private val appContext = context.applicationContext
    private val bleScanner = BlePcScanner(appContext)
    private val nsdDiscoverer = NsdServiceDiscoverer(appContext)

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fusionBreakdown = MutableStateFlow<List<Pair<NearbyDevice, FusionBreakdown>>>(emptyList())
    val fusionBreakdown: StateFlow<List<Pair<NearbyDevice, FusionBreakdown>>> = _fusionBreakdown.asStateFlow()

    override suspend fun refresh() {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            coroutineScope {
                val bleDeferred = async { bleScanner.scanOnce(timeoutMs = 10_000L) }
                val mdnsDeferred = async { nsdDiscoverer.discoverAllPcs(timeoutMs = 10_000L) }

                val bleResults = bleDeferred.await()
                val mdnsResults = mdnsDeferred.await()

                val merged = DiscoveryMerger.merge(bleResults, mdnsResults)
                val scored = BasicFusionScorer.scoreAll(merged)
                _fusionBreakdown.value = scored
                _devices.value = scored.map { it.first }

                if (_devices.value.isEmpty()) {
                    _errorMessage.value = when {
                        bleResults.isEmpty() && mdnsResults.isEmpty() ->
                            "No SpaceDrop PC found nearby — check PC tray app and Bluetooth"
                        mdnsResults.isEmpty() ->
                            "No PC on Wi-Fi — try moving closer for BLE"
                        else ->
                            "No nearby PCs passed proximity filter"
                    }
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    override fun findById(deviceId: String): NearbyDevice? =
        _devices.value.find { it.id == deviceId }

    override fun stopDiscovery() {
        bleScanner.stopScan()
        nsdDiscoverer.stopDiscovery()
    }
}
