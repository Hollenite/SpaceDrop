package com.prototype.airdrop.core.connectivity.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.nio.charset.Charset
import kotlin.coroutines.resume

class BlePcScanner(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val mainHandler = Handler(Looper.getMainLooper())

    private var scanCallback: ScanCallback? = null
    private val inFlightAddresses = mutableSetOf<String>()
    private val results = linkedMapOf<String, DiscoveredBlePc>()

    suspend fun scanOnce(timeoutMs: Long = 10_000L): List<DiscoveredBlePc> {
        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth unavailable or disabled")
            return emptyList()
        }
        if (!BlePermissions.hasAll(appContext)) {
            log("Missing BLE permissions")
            return emptyList()
        }

        results.clear()
        inFlightAddresses.clear()

        return withTimeoutOrNull(timeoutMs + 500L) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        handleScanResult(result)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        log("BLE scan failed error=$errorCode")
                        stopScanInternal()
                        if (continuation.isActive) {
                            continuation.resume(results.values.toList())
                        }
                    }
                }

                scanCallback = callback
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(SpaceDropBleUuids.SERVICE))
                        .build()
                )
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                continuation.invokeOnCancellation {
                    stopScanInternal()
                }

                runCatching {
                    adapter.bluetoothLeScanner.startScan(filters, settings, callback)
                }.onFailure { error ->
                    log("startScan failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                    return@suspendCancellableCoroutine
                }

                mainHandler.postDelayed({
                    stopScanInternal()
                    if (continuation.isActive) {
                        continuation.resume(results.values.toList())
                    }
                }, timeoutMs)
            }
        } ?: results.values.toList()
    }

    fun stopScan() {
        stopScanInternal()
    }

    private fun stopScanInternal() {
        scanCallback?.let { callback ->
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        if (address in inFlightAddresses) return
        if (results.containsKey(address)) {
            val existing = results[address]!!
            results[address] = existing.copy(
                rssi = result.rssi,
                lastSeenMs = System.currentTimeMillis()
            )
            return
        }

        inFlightAddresses.add(address)
        resolveGatt(device, result.rssi)
    }

    @SuppressLint("MissingPermission")
    private fun resolveGatt(device: BluetoothDevice, rssi: Int) {
        device.connectGatt(
            appContext,
            false,
            object : BluetoothGattCallback() {
                private val values = mutableMapOf<String, String>()

                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        g.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        finish(g)
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        finish(g)
                        return
                    }
                    val service = g.getService(SpaceDropBleUuids.SERVICE)
                    if (service == null) {
                        finish(g)
                        return
                    }
                    readNext(g, listOf(
                        SpaceDropBleUuids.DEVICE_ID to "deviceId",
                        SpaceDropBleUuids.DEVICE_NAME to "deviceName",
                        SpaceDropBleUuids.ENDPOINT to "endpoint"
                    ))
                }

                override fun onCharacteristicRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        values[characteristic.uuid.toString()] =
                            String(value, Charset.forName("UTF-8")).trim()
                    }
                    readNext(g, pendingReads)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        @Suppress("DEPRECATION")
                        val value = characteristic.value ?: byteArrayOf()
                        values[characteristic.uuid.toString()] =
                            String(value, Charset.forName("UTF-8")).trim()
                    }
                    readNext(g, pendingReads)
                }

                private var pendingReads: List<Pair<java.util.UUID, String>> = emptyList()

                private fun readNext(g: BluetoothGatt, queue: List<Pair<java.util.UUID, String>>) {
                    if (queue.isEmpty()) {
                        publishResult(g, rssi)
                        finish(g)
                        return
                    }
                    pendingReads = queue.drop(1)
                    val (uuid, _) = queue.first()
                    val characteristic = g.getService(SpaceDropBleUuids.SERVICE)?.getCharacteristic(uuid)
                    if (characteristic == null) {
                        readNext(g, pendingReads)
                        return
                    }
                    val started = g.readCharacteristic(characteristic)
                    if (!started) {
                        readNext(g, pendingReads)
                    }
                }

                private fun publishResult(g: BluetoothGatt, rssi: Int) {
                    val deviceId = values[SpaceDropBleUuids.DEVICE_ID.toString()] ?: return
                    val name = values[SpaceDropBleUuids.DEVICE_NAME.toString()] ?: deviceId
                    val endpoint = parseEndpoint(values[SpaceDropBleUuids.ENDPOINT.toString()]) ?: return
                    results[device.address] = DiscoveredBlePc(
                        deviceId = deviceId,
                        name = name,
                        host = endpoint.first,
                        port = endpoint.second,
                        rssi = rssi
                    )
                    log("Resolved BLE PC $deviceId at ${endpoint.first}:${endpoint.second} rssi=$rssi")
                }

                private fun finish(g: BluetoothGatt) {
                    inFlightAddresses.remove(device.address)
                    runCatching { g.close() }
                }
            },
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private fun parseEndpoint(json: String?): Pair<String, Int>? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(json)
            obj.getString("host") to obj.getInt("port")
        }.getOrNull()
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "SpaceDropBLE"
    }
}
