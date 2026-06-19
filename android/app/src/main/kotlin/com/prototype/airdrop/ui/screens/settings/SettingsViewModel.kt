package com.prototype.airdrop.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.MockScenario
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {

    private val repository = AppDependencies.repository

    val currentScenario: StateFlow<MockScenario> = repository.scenario

    val devices: StateFlow<List<NearbyDevice>> = repository.devices

    fun setScenario(scenario: MockScenario) {
        repository.setScenario(scenario)
        AppDependencies.transferEngine.reset()
    }
}
