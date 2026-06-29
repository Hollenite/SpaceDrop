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
    private var serviceInfoCallback: NsdManager.ServiceInfoCallback? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * One-shot discovery. Returns the resolved IPv4 host, or null on timeout / failure.
     * All NsdManager calls run on the main thread.
     */
    suspend fun discoverOnce(timeoutMs: Long = 10_000L): String? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                for (serviceType in SERVICE_TYPES) {
                    log("Trying service type: $serviceType")
                    val host = discoverWithType(serviceType)
                    if (host != null) {
                        log("Resolved host $host via $serviceType")
                        return@withTimeoutOrNull host
                    }
                }
                log("No service found after trying all types")
                null
            }
        }

    private suspend fun discoverWithType(serviceType: String): String? =
        suspendCancellableCoroutine { continuation ->
            var finished = false
            var listener: NsdManager.DiscoveryListener? = null

            fun cleanup() {
                serviceInfoCallback?.let {
                    runCatching { nsdManager.unregisterServiceInfoCallback(it) }
                }
                serviceInfoCallback = null
                listener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
                listener = null
                discoveryListener = null
                releaseMulticastLock()
            }

            fun finish(result: String?) {
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
                    log("Service found: ${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
                    resolveService(serviceInfo, onResolved = { host ->
                        if (host != null) finish(host)
                    }, onFailed = {
                        log("Resolve failed for ${serviceInfo.serviceName}")
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(type: String) {
                    log("Discovery stopped for $type")
                }

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    log("Start discovery failed for $type error=$errorCode")
                    finish(null)
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
                finish(null)
            }
        }

    private fun resolveService(
        serviceInfo: NsdServiceInfo,
        onResolved: (String?) -> Unit,
        onFailed: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfoCallback?.let {
                runCatching { nsdManager.unregisterServiceInfoCallback(it) }
            }
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) {
                    val host = resolveHost(info)
                    log("Service updated: ${info.serviceName} host=$host")
                    if (host != null) {
                        runCatching { nsdManager.unregisterServiceInfoCallback(this) }
                        serviceInfoCallback = null
                        onResolved(host)
                    }
                }

                override fun onServiceLost() {
                    log("Service lost during resolve: ${serviceInfo.serviceName}")
                    runCatching { nsdManager.unregisterServiceInfoCallback(this) }
                    serviceInfoCallback = null
                    onFailed()
                }

                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    log("ServiceInfoCallback registration failed error=$errorCode")
                    serviceInfoCallback = null
                    onFailed()
                }

                override fun onServiceInfoCallbackUnregistered() {
                    serviceInfoCallback = null
                }
            }
            serviceInfoCallback = callback
            runCatching {
                nsdManager.registerServiceInfoCallback(serviceInfo, mainExecutor, callback)
            }.onFailure { error ->
                log("registerServiceInfoCallback failed: ${error.message}")
                serviceInfoCallback = null
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
                        val host = resolveHost(info)
                        log("resolveService resolved host=$host")
                        onResolved(host)
                    }
                }
            )
        }
    }

    fun stopDiscovery() {
        serviceInfoCallback?.let {
            runCatching { nsdManager.unregisterServiceInfoCallback(it) }
        }
        serviceInfoCallback = null
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
