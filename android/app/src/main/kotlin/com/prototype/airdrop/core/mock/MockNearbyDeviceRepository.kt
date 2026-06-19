package com.prototype.airdrop.core.mock

import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.core.model.TrustState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockNearbyDeviceRepository {

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    private val _scenario = MutableStateFlow(MockScenario.MULTIPLE_DEVICES)
    val scenario: StateFlow<MockScenario> = _scenario.asStateFlow()

    private val dishasLaptop = NearbyDevice(
        id = "device-disha-laptop",
        name = "Disha's Laptop",
        trustState = TrustState.PAIRED,
        proximityState = ProximityState.READY_NEARBY,
        isReady = true,
        confidence = 0.95f
    )

    private val officePC = NearbyDevice(
        id = "device-office-pc",
        name = "Office PC",
        trustState = TrustState.NEW_DEVICE,
        proximityState = ProximityState.NEARBY,
        isReady = false,
        confidence = 0.80f
    )

    private val labDesktop = NearbyDevice(
        id = "device-lab-desktop",
        name = "Lab Desktop",
        trustState = TrustState.PAIRED,
        proximityState = ProximityState.WEAK_SIGNAL,
        isReady = true,
        confidence = 0.45f
    )

    init {
        applyScenario(MockScenario.MULTIPLE_DEVICES)
    }

    fun setScenario(scenario: MockScenario) {
        _scenario.value = scenario
        applyScenario(scenario)
    }

    fun pairDevice(deviceId: String) {
        _devices.value = _devices.value.map { device ->
            if (device.id == deviceId) {
                device.copy(
                    trustState = TrustState.PAIRED,
                    proximityState = ProximityState.READY_NEARBY,
                    isReady = true
                )
            } else {
                device
            }
        }
    }

    fun findById(deviceId: String): NearbyDevice? =
        _devices.value.find { it.id == deviceId }

    private fun applyScenario(scenario: MockScenario) {
        _devices.value = when (scenario) {
            MockScenario.PAIRED_DEVICE_NEARBY -> listOf(dishasLaptop)
            MockScenario.NEW_DEVICE_ONLY -> listOf(officePC)
            MockScenario.NO_DEVICES -> emptyList()
            MockScenario.MULTIPLE_DEVICES -> listOf(dishasLaptop, officePC, labDesktop)
            MockScenario.TRANSFER_FAILURE -> listOf(dishasLaptop)
        }
    }
}
