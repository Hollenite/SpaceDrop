package com.prototype.airdrop.core.connectivity

import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SpaceDropHandshakeClient(
    private val deviceIdStore: DeviceIdStore
) {

    fun ping(host: String, port: Int = HANDSHAKE_PORT): ConnectionResult {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val deviceName = Build.MODEL ?: "Android Device"
            val deviceId = deviceIdStore.getOrCreateDeviceId()

            val pingJson = JSONObject().apply {
                put("type", "PING")
                put("device_name", deviceName)
                put("device_id", deviceId)
                put("token", DEV_TOKEN)
            }

            // Do not use .use {} on socket streams — closing the writer closes the socket.
            socket.getOutputStream().bufferedWriter(Charsets.UTF_8).apply {
                write(pingJson.toString())
                newLine()
                flush()
            }

            val responseLine = socket.getInputStream()
                .bufferedReader(Charsets.UTF_8)
                .readLine()

            if (responseLine.isNullOrBlank()) {
                return failure("Empty response from $host")
            }

            val response = JSONObject(responseLine)
            if (response.optString("type") != "PONG") {
                return failure("Unexpected response type: ${response.optString("type")}")
            }

            val pcName = response.optString("device_name", "PC")
            ConnectionResult.Success(pcName)
        } catch (error: ConnectException) {
            failure("Connection refused — check PC IP and Windows Firewall on port $port")
        } catch (error: SocketTimeoutException) {
            failure("Connection timed out to $host:$port")
        } catch (error: Exception) {
            failure("${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun failure(reason: String): ConnectionResult.Failure {
        Log.d(TAG, reason)
        return ConnectionResult.Failure(reason)
    }

    companion object {
        private const val TAG = "SpaceDropTCP"
        const val HANDSHAKE_PORT = 58392
        private const val DEV_TOKEN = "spacedrop-dev-token"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
    }
}
