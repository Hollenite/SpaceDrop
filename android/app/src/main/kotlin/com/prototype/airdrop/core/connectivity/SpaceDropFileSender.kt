package com.prototype.airdrop.core.connectivity

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import com.prototype.airdrop.core.model.TransferItem
import org.json.JSONObject
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SpaceDropFileSender(
    private val deviceIdStore: DeviceIdStore
) {

    data class SendResult(
        val success: Boolean,
        val pcName: String? = null,
        val error: String? = null
    )

    data class ProgressUpdate(
        val bytesSent: Long,
        val totalBytes: Long,
        val progressPercent: Int,
        val speedText: String
    )

    fun sendFile(
        host: String,
        port: Int,
        item: TransferItem,
        contentResolver: ContentResolver,
        onProgress: (ProgressUpdate) -> Unit
    ): SendResult {
        var socket: Socket? = null
        return try {
            if (item.contentUri == Uri.EMPTY) {
                return SendResult(false, error = "Missing file URI")
            }

            val totalBytes = item.sizeBytes ?: contentResolver.openFileDescriptor(item.contentUri, "r")
                ?.use { it.statSize } ?: -1L
            if (totalBytes <= 0) {
                return SendResult(false, error = "Could not determine file size")
            }

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
            writeLine(socket, pingJson.toString())

            val pongLine = readLine(socket) ?: return SendResult(false, error = "Empty PONG")
            val pong = JSONObject(pongLine)
            if (pong.optString("type") != "PONG") {
                return SendResult(false, error = "Unexpected response: ${pong.optString("type")}")
            }
            val pcName = pong.optString("device_name", "PC")

            val metaJson = JSONObject().apply {
                put("type", "FILE_META")
                put("name", item.displayName)
                put("size", totalBytes)
                put("mime", item.mimeType ?: "application/octet-stream")
                put("token", DEV_TOKEN)
            }
            writeLine(socket, metaJson.toString())

            val inputStream: InputStream = contentResolver.openInputStream(item.contentUri)
                ?: return SendResult(false, error = "Could not open file")

            inputStream.use { input ->
                val output = socket.getOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesSent = 0L
                var lastProgressTime = System.currentTimeMillis()
                var lastBytesSent = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    bytesSent += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime >= 200 || bytesSent == totalBytes) {
                        val elapsedSec = (now - lastProgressTime).coerceAtLeast(1L) / 1000.0
                        val deltaBytes = bytesSent - lastBytesSent
                        val speedBps = deltaBytes / elapsedSec
                        val speedText = formatSpeed(speedBps)
                        val progress = ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(ProgressUpdate(bytesSent, totalBytes, progress, speedText))
                        lastProgressTime = now
                        lastBytesSent = bytesSent
                    }
                }
                output.flush()
            }

            val ackLine = readLine(socket) ?: return SendResult(false, error = "Empty FILE_ACK")
            val ack = JSONObject(ackLine)
            if (ack.optString("type") != "FILE_ACK" || !ack.optBoolean("ok", false)) {
                return SendResult(
                    false,
                    error = ack.optString("error", "Transfer rejected by PC")
                )
            }

            SendResult(success = true, pcName = pcName)
        } catch (error: ConnectException) {
            SendResult(false, error = "Connection refused — check PC IP and firewall on port $port")
        } catch (error: SocketTimeoutException) {
            SendResult(false, error = "Connection timed out to $host:$port")
        } catch (error: Exception) {
            Log.d(TAG, "sendFile failed: ${error.message}")
            SendResult(false, error = "${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun writeLine(socket: Socket, line: String) {
        socket.getOutputStream().bufferedWriter(Charsets.UTF_8).apply {
            write(line)
            newLine()
            flush()
        }
    }

    private fun readLine(socket: Socket): String? {
        return socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000)
            bytesPerSecond >= 1_000 -> String.format("%.0f KB/s", bytesPerSecond / 1_000)
            else -> String.format("%.0f B/s", bytesPerSecond)
        }
    }

    companion object {
        private const val TAG = "SpaceDropFile"
        private const val DEV_TOKEN = "spacedrop-dev-token"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 32 * 1024
    }
}
