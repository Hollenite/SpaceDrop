package com.prototype.airdrop.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel : ViewModel() {
    private val _isReady = MutableStateFlow(true)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
}
