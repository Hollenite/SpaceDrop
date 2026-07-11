package com.prototype.airdrop.core.connectivity

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.prototype.airdrop.core.model.TransferItem
import org.json.JSONObject
import java.io.InputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SpaceDropFileSender(
    private val deviceIdStore: DeviceIdStore,
    private val deviceNameProvider: () -> String = { DeviceNameStore.defaultName() }
) {

    data class SendResult(
        val success: Boolean,
        val pcName: String? = null,
        val error: String? = null
    )

    data class ProgressUpdate(
        /** Overall bytes sent across the whole batch. */
        val bytesSent: Long,
        /** Overall total bytes across the whole batch. */
        val totalBytes: Long,
        /** Overall percent across the whole batch. */
        val progressPercent: Int,
        val speedText: String,
        /** 0-based index of the file currently being sent. */
        val fileIndex: Int = 0,
        val fileCount: Int = 1,
        val fileName: String = ""
    )

    /** Single-file convenience wrapper over [sendFiles]. */
    fun sendFile(
        host: String,
        port: Int,
        item: TransferItem,
        contentResolver: ContentResolver,
        onProgress: (ProgressUpdate) -> Unit
    ): SendResult = sendFiles(host, port, listOf(item), contentResolver, onProgress)

    /**
     * Sends 1..N files to a PC over a single TCP connection: one PING/PONG handshake, then a
     * FILE_META -> raw bytes -> FILE_ACK cycle per file, in order. Progress is reported as overall
     * batch progress plus the current file index/name. Fails fast on the first rejected/failed file.
     *
     * Control replies (PONG, FILE_ACKs) are read through a single persistent reader; the client only
     * writes file bytes and never reads them, so there is no read-ahead interleaving to worry about.
     */
    fun sendFiles(
        host: String,
        port: Int,
        items: List<TransferItem>,
        contentResolver: ContentResolver,
        onProgress: (ProgressUpdate) -> Unit
    ): SendResult {
        if (items.isEmpty()) return SendResult(false, error = "No files to send")

        val sizes = LongArray(items.size)
        var overallTotal = 0L
        for ((index, item) in items.withIndex()) {
            if (item.contentUri == Uri.EMPTY) {
                return SendResult(false, error = "Missing file URI (${item.displayName})")
            }
            val size = item.sizeBytes ?: contentResolver.openFileDescriptor(item.contentUri, "r")
                ?.use { it.statSize } ?: -1L
            if (size <= 0) {
                return SendResult(false, error = "Could not determine size of ${item.displayName}")
            }
            sizes[index] = size
            overallTotal += size
        }

        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val output = socket.getOutputStream()
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)

            val pingJson = JSONObject().apply {
                put("type", "PING")
                put("device_name", deviceNameProvider())
                put("device_id", deviceIdStore.getOrCreateDeviceId())
                put("token", DEV_TOKEN)
                put("count", items.size)
            }
            writeLine(output, pingJson.toString())

            val pongLine = reader.readLine() ?: return SendResult(false, error = "Empty PONG")
            val pong = JSONObject(pongLine)
            if (pong.optString("type") != "PONG") {
                return SendResult(false, error = "Unexpected response: ${pong.optString("type")}")
            }
            val pcName = pong.optString("device_name", "PC")

            var overallSent = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastBytesSent = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            for ((index, item) in items.withIndex()) {
                val metaJson = JSONObject().apply {
                    put("type", "FILE_META")
                    put("name", item.displayName)
                    put("size", sizes[index])
                    put("mime", item.mimeType ?: "application/octet-stream")
                    put("token", DEV_TOKEN)
                }
                writeLine(output, metaJson.toString())

                val inputStream: InputStream = contentResolver.openInputStream(item.contentUri)
                    ?: return SendResult(false, error = "Could not open ${item.displayName}")

                inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        overallSent += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 200 || overallSent == overallTotal) {
                            val elapsedSec = (now - lastProgressTime).coerceAtLeast(1L) / 1000.0
                            val speedBps = (overallSent - lastBytesSent) / elapsedSec
                            val progress = ((overallSent * 100) / overallTotal).toInt().coerceIn(0, 100)
                            onProgress(
                                ProgressUpdate(
                                    bytesSent = overallSent,
                                    totalBytes = overallTotal,
                                    progressPercent = progress,
                                    speedText = formatSpeed(speedBps),
                                    fileIndex = index,
                                    fileCount = items.size,
                                    fileName = item.displayName
                                )
                            )
                            lastProgressTime = now
                            lastBytesSent = overallSent
                        }
                    }
                    output.flush()
                }

                val ackLine = reader.readLine()
                    ?: return SendResult(false, error = "Empty FILE_ACK for ${item.displayName}")
                val ack = JSONObject(ackLine)
                if (ack.optString("type") != "FILE_ACK" || !ack.optBoolean("ok", false)) {
                    return SendResult(
                        false,
                        error = ack.optString("error", "PC rejected ${item.displayName}")
                    )
                }
            }

            SendResult(success = true, pcName = pcName)
        } catch (error: ConnectException) {
            SendResult(false, error = "Connection refused — check PC IP and firewall on port $port")
        } catch (error: SocketTimeoutException) {
            SendResult(false, error = "Connection timed out to $host:$port")
        } catch (error: Exception) {
            Log.d(TAG, "sendFiles failed: ${error.message}")
            SendResult(false, error = "${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun writeLine(output: java.io.OutputStream, line: String) {
        output.write((line + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
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
