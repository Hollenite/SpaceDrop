package com.prototype.airdrop.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppDependencies.fusedNearbyDeviceRepository(application)

    val devices: StateFlow<List<NearbyDevice>> = repository.devices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rankedDevices: StateFlow<List<RankedNearbyDevice>> = repository.rankedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoading: StateFlow<Boolean> = repository.isLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val errorMessage: StateFlow<String?> = repository.errorMessage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        repository.startPeriodicRefresh(intervalMs = 2_000L)
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopPeriodicRefresh()
    }
}
