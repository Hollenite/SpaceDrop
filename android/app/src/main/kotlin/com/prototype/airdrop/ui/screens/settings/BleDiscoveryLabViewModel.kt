package com.prototype.airdrop.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.discovery.FusionBreakdown
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BleDiscoveryLabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppDependencies.fusedNearbyDeviceRepository(application)

    val devices: StateFlow<List<NearbyDevice>> = repository.devices
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage
    val fusionBreakdown: StateFlow<List<Pair<NearbyDevice, FusionBreakdown>>> = repository.fusionBreakdown
    val phoneBssid: StateFlow<String?> = repository.phoneBssid

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    init {
        // Run the mic listener while the lab is open so acoustic corroboration can appear.
        repository.ensureAcousticListening()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            appendLog("Starting fused BLE + mDNS scan…")
            repository.refresh()
            val phone = repository.phoneBssid.value
            appendLog("Phone BSSID: ${phone ?: "unavailable"}")
            val breakdown = repository.fusionBreakdown.value
            if (breakdown.isEmpty()) {
                appendLog("No devices after fusion")
            } else {
                breakdown.forEach { (device, fusion) ->
                    appendLog(
                        "${device.name} id=${device.id} conf=${device.confidence} " +
                            "prox=${device.proximityState} ble=${fusion.hasBle} mdns=${fusion.hasMdns} " +
                            "rssi=${fusion.rssi} wifi=${fusion.bssidMatch} " +
                            "phoneBssid=${fusion.phoneBssid} pcBssid=${fusion.pcBssid} " +
                            "signals=${fusion.signalSummary} acoustic=${fusion.acousticMatch} " +
                            "wBle=${fusion.bleRssiWeight} wMdns=${fusion.mdnsWeight} wAgree=${fusion.agreementWeight} " +
                            "wBssid=${fusion.bssidWeight} wHost=${fusion.hostWeight} wAcoustic=${fusion.acousticWeight}"
                    )
                }
            }
        }
    }

    private fun appendLog(line: String) {
        _logLines.value = _logLines.value + line
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopDiscovery()
    }
}
