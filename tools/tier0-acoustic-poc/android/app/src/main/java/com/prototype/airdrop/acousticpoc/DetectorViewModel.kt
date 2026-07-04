package com.prototype.airdrop.acousticpoc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DetectorViewModel(application: Application) : AndroidViewModel(application) {

    private val _targetFrequencyHz = MutableStateFlow(19_000f)
    val targetFrequencyHz: StateFlow<Float> = _targetFrequencyHz.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _metrics = MutableStateFlow<ToneMetrics?>(null)
    val metrics: StateFlow<ToneMetrics?> = _metrics.asStateFlow()

    private val listener = AudioToneListener(
        context = application,
        targetFrequencyProvider = { _targetFrequencyHz.value },
        onMetrics = { _metrics.value = it }
    )

    fun setTargetFrequency(hz: Float) {
        _targetFrequencyHz.value = hz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
    }

    fun startListening() {
        if (_isListening.value) return
        _isListening.value = true
        listener.start(viewModelScope)
    }

    fun stopListening() {
        if (!_isListening.value) return
        listener.stop()
        _isListening.value = false
    }

    override fun onCleared() {
        listener.stop()
        super.onCleared()
    }

    companion object {
        const val MIN_FREQ_HZ = 17_000f
        const val MAX_FREQ_HZ = 21_000f
    }
}
