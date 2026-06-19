package com.prototype.airdrop.ui.screens.pairing

import androidx.lifecycle.ViewModel
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PairingViewModel : ViewModel() {

    private val repository = AppDependencies.repository

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    val pairingCode: String = generateCode()

    fun findDevice(deviceId: String): NearbyDevice? = repository.findById(deviceId)

    fun confirmPairing(deviceId: String) {
        repository.pairDevice(deviceId)
        val device = repository.findById(deviceId)
        if (device != null) {
            AppState.selectedDevice = device
        }
        _isPaired.value = true
    }

    private fun generateCode(): String {
        val digits = (100_000..999_999).random()
        return digits.toString()
    }
}
