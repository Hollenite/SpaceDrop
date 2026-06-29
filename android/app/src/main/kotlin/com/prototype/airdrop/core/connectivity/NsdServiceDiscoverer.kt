package com.prototype.airdrop.core.connectivity

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class NsdServiceDiscoverer(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(appContext)

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * One-shot discovery. Returns the resolved IPv4 host, or null on timeout / failure.
     * All NsdManager calls run on the main thread.
     */
    suspend fun discoverOnce(timeoutMs: Long = 10_000L): String? =
        discoverPc(timeoutMs)?.host

    suspend fun discoverAllPcs(timeoutMs: Long = 10_000L): List<DiscoveredPc> =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                val collected = mutableListOf<DiscoveredPc>()
                for (serviceType in SERVICE_TYPES) {
                    log("Trying service type: $serviceType")
                    val pcs = discoverAllWithType(serviceType, timeoutMs / SERVICE_TYPES.size)
                    collected.addAll(pcs)
                    if (collected.isNotEmpty()) break
                }
                collected.distinctBy { "${it.deviceId ?: it.host}:${it.port}" }
            } ?: emptyList()
        }

    /**
     * One-shot discovery. Returns resolved PC service name + IPv4 host, or null on timeout.
     */
    suspend fun discoverPc(timeoutMs: Long = 10_000L): DiscoveredPc? =
        discoverAllPcs(timeoutMs).firstOrNull()
    private suspend fun discoverAllWithType(serviceType: String, timeoutMs: Long): List<DiscoveredPc> =
        suspendCancellableCoroutine { continuation ->
            var finished = false
            var listener: NsdManager.DiscoveryListener? = null
            val resolved = mutableListOf<DiscoveredPc>()
            val pendingResolves = mutableSetOf<String>()

            fun cleanup() {
                listener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
                listener = null
                discoveryListener = null
                releaseMulticastLock()
            }

            fun finish(result: List<DiscoveredPc>) {
                if (finished) return
                finished = true
                cleanup()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            acquireMulticastLock()

            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    log("Discovery started for $type")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (finished) return
                    if (!isSpaceDropService(serviceInfo)) {
                        log("Ignoring unrelated service: ${serviceInfo.serviceType}")
                        return
                    }
                    val serviceName = serviceInfo.serviceName ?: return
                    if (serviceName in pendingResolves) return
                    pendingResolves.add(serviceName)
                    log("Service found: $serviceName type=${serviceInfo.serviceType}")
                    resolveService(serviceInfo, onResolved = { pc ->
                        pendingResolves.remove(serviceName)
                        if (pc != null) {
                            resolved.add(pc)
                            log("Resolved ${pc.serviceName} at ${pc.host}:${pc.port} id=${pc.deviceId}")
                        }
                    }, onFailed = {
                        pendingResolves.remove(serviceName)
                        log("Resolve failed for $serviceName")
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(type: String) {
                    log("Discovery stopped for $type")
                }

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    log("Start discovery failed for $type error=$errorCode")
                    finish(emptyList())
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    log("Stop discovery failed for $type error=$errorCode")
                }
            }

            discoveryListener = listener

            continuation.invokeOnCancellation {
                cleanup()
            }

            runCatching {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )
            }.onFailure { error ->
                log("discoverServices threw: ${error.message}")
                finish(emptyList())
                return@suspendCancellableCoroutine
            }

            android.os.Handler(appContext.mainLooper).postDelayed({
                finish(resolved.toList())
            }, timeoutMs.coerceAtLeast(1_000L))
        }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (DiscoveredPc?) -> Unit,
        onFailed: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) {
                    val pc = toDiscoveredPc(info)
                    log("Service updated: ${info.serviceName} host=${pc?.host}")
                    if (pc != null) {
                        runCatching { nsdManager.unregisterServiceInfoCallback(this) }
                        onResolved(pc)
                    }
                }

                override fun onServiceLost() {
                    log("Service lost during resolve: ${serviceInfo.serviceName}")
                    runCatching { nsdManager.unregisterServiceInfoCallback(this) }
                    onFailed()
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    log("ServiceInfoCallback registration failed error=$errorCode")
                    onFailed()
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            runCatching {
                nsdManager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
            }.onFailure { error ->
                log("registerServiceInfoCallback failed: ${error.message}")
                onFailed()
            }
        } else {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        log("resolveService failed error=$errorCode")
                        onFailed()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val pc = toDiscoveredPc(info)
                        log("resolveService resolved host=${pc?.host}")
                        onResolved(pc)
                    }
                }
            )
        }
    }

    private fun toDiscoveredPc(info: NsdServiceInfo): DiscoveredPc? {
        val host = resolveHost(info) ?: return null
        val port = if (info.port > 0) info.port else SpaceDropHandshakeClient.HANDSHAKE_PORT
        val deviceId = parseTxtAttribute(info, "device_id")
        val bssid = parseTxtAttribute(info, "bssid")?.lowercase()
        return DiscoveredPc(
            serviceName = info.serviceName ?: host,
            host = host,
            port = port,
            deviceId = deviceId,
            bssid = bssid
        )
    }

    private fun parseTxtAttribute(info: NsdServiceInfo, key: String): String? {
        val attributes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.attributes
        } else {
            @Suppress("DEPRECATION")
            info.attributes
        } ?: return null
        val bytes = attributes[key] ?: return null
        return String(bytes, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        releaseMulticastLock()
    }

    private fun resolveHost(info: NsdServiceInfo): String? {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(info.host)
        }
        return addresses
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
            ?: addresses.firstOrNull()?.hostAddress
    }

    private fun isSpaceDropService(info: NsdServiceInfo): Boolean {
        val type = info.serviceType ?: return false
        return type.contains(SERVICE_MATCH)
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("spacedrop-nsd").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        multicastLock = null
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "SpaceDropNSD"
        private const val SERVICE_MATCH = "_spacedrop._tcp"

        private val SERVICE_TYPES = listOf(
            "_spacedrop._tcp.",
            "_spacedrop._tcp",
            "_spacedrop._tcp.local."
        )
    }
}
