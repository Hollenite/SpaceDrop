package com.prototype.airdrop.ui.screens.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.connectivity.DeviceIdStore
import com.prototype.airdrop.core.connectivity.DeviceNameStore
import com.prototype.airdrop.core.discovery.FusedNearbyDeviceRepository
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.security.IdentityKeyStore
import com.prototype.airdrop.core.security.PairedPeerStore
import com.prototype.airdrop.core.security.PairingClient
import com.prototype.airdrop.core.security.PairingOffer
import com.prototype.airdrop.core.security.PairingQrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val peers = PairedPeerStore(application)
    private val identity = IdentityKeyStore(application).also { it.loadOrCreate() }
    private val deviceNameStore = DeviceNameStore(application)
    private val pairingClient = PairingClient(
        identity,
        peers,
        DeviceIdStore(application),
        deviceNameProvider = { deviceNameStore.getName() }
    )
    private val fused: FusedNearbyDeviceRepository =
        AppDependencies.fusedNearbyDeviceRepository(application)

    private val _status = MutableStateFlow("Scan the QR shown on your PC tray (Pair phone).")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun findDevice(deviceId: String): NearbyDevice? =
        fused.devices.value.firstOrNull { it.id == deviceId }

    fun onQrScanned(raw: String) {
        val offer = PairingQrParser.parse(raw.trim())
        if (offer == null) {
            _status.value = "Not a SpaceDrop pairing QR"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Connecting to PC…"
            val host = resolveHost(offer)
            if (host == null) {
                _status.value =
                    "Found QR for ${offer.deviceId.take(8)}… but no LAN address yet. " +
                        "Keep the PC tray running on the same Wi‑Fi and try again."
                _busy.value = false
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                pairingClient.pair(offer, host)
            }
            _busy.value = false
            _status.value = result.message
            _isPaired.value = result.success
            if (result.success) {
                fused.refreshQuick()
            }
        }
    }

    private suspend fun resolveHost(offer: PairingOffer): String? {
        fused.refreshQuick()
        val fromList = fused.devices.value.firstOrNull { it.id == offer.deviceId }?.host
        if (!fromList.isNullOrBlank()) return fromList
        // One more discovery pass
        fused.refresh()
        return fused.devices.value.firstOrNull { it.id == offer.deviceId }?.host
    }
}
