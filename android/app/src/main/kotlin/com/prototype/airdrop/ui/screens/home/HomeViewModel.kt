package com.prototype.airdrop.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {

    private val repository = AppDependencies.repository

    val devices: StateFlow<List<NearbyDevice>> = repository.devices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
