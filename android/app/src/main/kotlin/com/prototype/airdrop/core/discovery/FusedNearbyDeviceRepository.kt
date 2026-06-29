package com.prototype.airdrop.core.discovery

import android.content.Context
import android.util.Log
import com.prototype.airdrop.core.connectivity.NsdServiceDiscoverer
import com.prototype.airdrop.core.connectivity.WifiNetworkSnapshot
import com.prototype.airdrop.core.connectivity.ble.BlePcScanner
import com.prototype.airdrop.core.model.NearbyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FusedNearbyDeviceRepository(
    context: Context,
    private val bleScanner: BlePcScanner
) : NearbyDeviceRepository {

    private val appContext = context.applicationContext
    private val nsdDiscoverer = NsdServiceDiscoverer(appContext)
    private val usageStore = DeviceUsageStore(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val coalesceMutex = Mutex()
    private var periodicJob: Job? = null
    private var inFlightRefresh: Deferred<Unit>? = null

    private var lastRefreshCompletedAtMs: Long = 0L

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    private val _rankedDevices = MutableStateFlow<List<RankedNearbyDevice>>(emptyList())
    val rankedDevices: StateFlow<List<RankedNearbyDevice>> = _rankedDevices.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fusionBreakdown = MutableStateFlow<List<Pair<NearbyDevice, FusionBreakdown>>>(emptyList())
    val fusionBreakdown: StateFlow<List<Pair<NearbyDevice, FusionBreakdown>>> = _fusionBreakdown.asStateFlow()

    private val _phoneBssid = MutableStateFlow<String?>(null)
    val phoneBssid: StateFlow<String?> = _phoneBssid.asStateFlow()

    val deviceUsageStore: DeviceUsageStore get() = usageStore

    fun hasCachedDevices(): Boolean = _rankedDevices.value.isNotEmpty()

    fun hasFreshCache(maxAgeMs: Long = 10_000L): Boolean =
        hasCachedDevices() &&
            (System.currentTimeMillis() - lastRefreshCompletedAtMs) <= maxAgeMs

    fun ensureBleContinuousScanIfNeeded() {
        bleScanner.startContinuousScan()
    }

    override suspend fun refresh() {
        coalescedRefresh(fullBleScan = true)
    }

    suspend fun refreshQuick() {
        coalescedRefresh(fullBleScan = false)
    }

    private suspend fun coalescedRefresh(fullBleScan: Boolean) {
        val deferred = coalesceMutex.withLock {
            inFlightRefresh?.takeIf { it.isActive }?.also {
                Log.d(TAG, "refresh coalesced fullBleScan=$fullBleScan")
            } ?: scope.async {
                refreshInternal(fullBleScan)
            }.also { inFlightRefresh = it }
        }
        try {
            deferred.await()
        } finally {
            coalesceMutex.withLock {
                if (inFlightRefresh === deferred && deferred.isCompleted) {
                    inFlightRefresh = null
                }
            }
        }
    }

    fun startPeriodicRefresh(intervalMs: Long = 2_000L) {
        if (periodicJob?.isActive == true) return
        bleScanner.startContinuousScan()
        periodicJob = scope.launch {
            refreshQuick()
            while (isActive) {
                delay(intervalMs)
                refreshQuick()
            }
        }
    }

    fun stopPeriodicRefresh() {
        periodicJob?.cancel()
        periodicJob = null
    }

    private suspend fun refreshInternal(fullBleScan: Boolean) {
        refreshMutex.withLock {
            val showBlockingLoad = _rankedDevices.value.isEmpty()
            if (showBlockingLoad) {
                _isLoading.value = true
            }
            _errorMessage.value = null

            val startedAt = System.currentTimeMillis()
            Log.d(
                TAG,
                "refresh start fullBleScan=$fullBleScan blocking=$showBlockingLoad " +
                    "cachedDevices=${_rankedDevices.value.size}"
            )

            try {
                WifiNetworkSnapshot.logAvailability(appContext)
                val phoneBssid = WifiNetworkSnapshot.currentBssid(appContext)
                _phoneBssid.value = phoneBssid

                val mdnsTimeoutMs = if (fullBleScan) 8_000L else 1_500L
                val (bleResults, mdnsResults) = coroutineScope {
                    val bleDeferred = async {
                        if (fullBleScan && periodicJob?.isActive != true) {
                            bleScanner.scanOnce(timeoutMs = 8_000L)
                        } else {
                            if (fullBleScan) delay(2_000L)
                            bleScanner.getCachedResults()
                        }
                    }
                    val mdnsDeferred = async {
                        nsdDiscoverer.discoverAllPcs(timeoutMs = mdnsTimeoutMs)
                    }
                    bleDeferred.await() to mdnsDeferred.await()
                }

                val merged = DiscoveryMerger.merge(bleResults, mdnsResults)
                val scored = FullFusionScorer.scoreAll(merged, phoneBssid)
                _fusionBreakdown.value = scored
                val deviceList = scored.map { it.first }
                _devices.value = deviceList
                _rankedDevices.value = DeviceRanking.rank(deviceList, usageStore)
                lastRefreshCompletedAtMs = System.currentTimeMillis()

                if (_devices.value.isEmpty()) {
                    _errorMessage.value = when {
                        bleResults.isEmpty() && mdnsResults.isEmpty() ->
                            "No SpaceDrop PC found nearby — check PC tray app and Bluetooth"
                        mdnsResults.isEmpty() ->
                            "No PC on Wi-Fi — try moving closer for BLE"
                        else ->
                            "No nearby PCs passed proximity filter"
                    }
                }

                val elapsedMs = System.currentTimeMillis() - startedAt
                Log.d(
                    TAG,
                    "refresh end fullBleScan=$fullBleScan elapsedMs=$elapsedMs " +
                        "devices=${deviceList.size} ble=${bleResults.size} mdns=${mdnsResults.size}"
                )
            } finally {
                if (showBlockingLoad) {
                    _isLoading.value = false
                }
            }
        }
    }

    override fun findById(deviceId: String): NearbyDevice? =
        _devices.value.find { it.id == deviceId }

    override fun stopDiscovery() {
        stopPeriodicRefresh()
        bleScanner.stopScan()
        nsdDiscoverer.stopDiscovery()
    }

    companion object {
        private const val TAG = "SpaceDropDiscovery"
    }
}
