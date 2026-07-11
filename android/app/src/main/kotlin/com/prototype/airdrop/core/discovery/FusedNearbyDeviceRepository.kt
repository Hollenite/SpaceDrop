package com.prototype.airdrop.core.discovery

import android.content.Context
import android.util.Log
import com.prototype.airdrop.core.acoustic.AcousticCorroborationSource
import com.prototype.airdrop.core.acoustic.AcousticListener
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
    private val bleScanner: BlePcScanner,
    private val acousticListener: AcousticListener? = null
) : NearbyDeviceRepository {

    private val acoustic: AcousticCorroborationSource? = acousticListener

    private val appContext = context.applicationContext
    private val nsdDiscoverer = NsdServiceDiscoverer(appContext)
    private val usageStore = DeviceUsageStore(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val coalesceMutex = Mutex()
    private var periodicJob: Job? = null
    private var inFlightRefresh: Deferred<Unit>? = null

    private var lastRefreshCompletedAtMs: Long = 0L

    // The blocking spinner is reserved for the very first refresh of the process. After one refresh
    // has completed (found or not), an empty result shows the persistent searching radar instead of
    // flipping isLoading true/false every periodic cycle (fixes the Home spinner<->radar flicker).
    @Volatile
    private var hasCompletedFirstRefresh = false

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

    /** Start the acoustic listener without periodic refresh (used by the debug lab's manual scan). */
    fun ensureAcousticListening() {
        acousticListener?.start(scope)
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
        acousticListener?.start(scope)
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
        acousticListener?.stop()
    }

    private suspend fun refreshInternal(fullBleScan: Boolean) {
        refreshMutex.withLock {
            val showBlockingLoad = !hasCompletedFirstRefresh && _rankedDevices.value.isEmpty()
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

                val mergedRaw = DiscoveryMerger.merge(bleResults, mdnsResults)
                val merged = applyAcousticCorroboration(mergedRaw)
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
                hasCompletedFirstRefresh = true
                if (showBlockingLoad) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Marks candidates whose device-id token was heard acoustically. Acoustic is corroboration
     * only: it can flag an existing BLE/mDNS candidate but never adds a row here.
     */
    private fun applyAcousticCorroboration(
        candidates: List<MergedDiscoveryCandidate>
    ): List<MergedDiscoveryCandidate> {
        val source = acoustic ?: return candidates
        if (candidates.isEmpty()) return candidates
        val matched = source.matchingDeviceIds(candidates.map { it.deviceId })
        if (matched.isEmpty()) return candidates
        Log.d(TAG, "acoustic corroboration matched=${matched.size} of ${candidates.size}")
        return candidates.map {
            if (it.deviceId in matched) it.copy(acousticTokenMatch = true) else it
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
