package com.prototype.airdrop.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prototype.airdrop.BuildConfig
import com.prototype.airdrop.core.connectivity.ConnectionResult
import com.prototype.airdrop.core.connectivity.DeviceIdStore
import com.prototype.airdrop.core.connectivity.DeviceNameStore
import com.prototype.airdrop.core.connectivity.NetworkUtils
import com.prototype.airdrop.core.connectivity.NsdServiceDiscoverer
import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionUiState {
    Idle,
    Connecting,
    Success,
    Failed
}

class RoomDiscoveryLabViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceIdStore = DeviceIdStore(application)
    private val deviceNameStore = DeviceNameStore(application)
    private val nsdDiscoverer = NsdServiceDiscoverer(application)
    private val handshakeClient = SpaceDropHandshakeClient(
        deviceIdStore,
        deviceNameProvider = { deviceNameStore.getName() }
    )

    private val _uiState = MutableStateFlow(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    fun initiateConnection() {
        if (_uiState.value == ConnectionUiState.Connecting) return

        viewModelScope.launch {
            if (!NetworkUtils.isOnWifi(getApplication())) {
                emitFailure(
                    userMessage = "Connect to Wi-Fi to discover nearby PCs",
                    debugDetail = "Device is not on Wi-Fi"
                )
                return@launch
            }

            _uiState.value = ConnectionUiState.Connecting
            setStatus("Discovering SpaceDrop PC on local network…")

            val host = nsdDiscoverer.discoverOnce()

            if (host == null) {
                emitFailure(
                    userMessage = "Connection failed",
                    debugDetail = "No mDNS service found within timeout"
                )
                return@launch
            }

            setStatus("Found $host — starting handshake…")
            if (NetworkUtils.isTailscaleOrCgnat(host)) {
                setStatus("Found $host (Tailscale/CGNAT) — ensure phone can reach this address")
            }
            connectToHost(host)
        }
    }

    fun connectManually(ip: String) {
        if (_uiState.value == ConnectionUiState.Connecting) return

        val parsed = NetworkUtils.parseHostAndPort(ip)
        if (parsed == null) {
            viewModelScope.launch {
                emitFailure(
                    userMessage = "Invalid IP address",
                    debugDetail = "Invalid host or port: ${ip.trim()}"
                )
            }
            return
        }

        val (host, port) = parsed

        viewModelScope.launch {
            _uiState.value = ConnectionUiState.Connecting
            setStatus("Connecting to $host:$port…")
            connectToHost(host, port)
        }
    }

    private suspend fun connectToHost(host: String, port: Int = SpaceDropHandshakeClient.HANDSHAKE_PORT) {
        val result = withContext(Dispatchers.IO) {
            handshakeClient.ping(host, port)
        }

        when (result) {
            is ConnectionResult.Success -> {
                _uiState.value = ConnectionUiState.Success
                setStatus("Connected to ${result.pcDeviceName}")
                _toastMessage.emit("Connected to ${result.pcDeviceName}")
                _uiState.value = ConnectionUiState.Idle
            }
            is ConnectionResult.Failure -> {
                emitFailure(
                    userMessage = "Connection failed",
                    debugDetail = result.reason ?: "Handshake failed"
                )
            }
        }
    }

    private suspend fun emitFailure(userMessage: String, debugDetail: String) {
        _uiState.value = ConnectionUiState.Failed
        setStatus(debugDetail)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, debugDetail)
        }
        _toastMessage.emit(userMessage)
        _uiState.value = ConnectionUiState.Idle
    }

    private fun setStatus(message: String) {
        _statusMessage.value = message
    }

    override fun onCleared() {
        super.onCleared()
        nsdDiscoverer.stopDiscovery()
    }

    companion object {
        private const val TAG = "SpaceDropLab"
    }
}
