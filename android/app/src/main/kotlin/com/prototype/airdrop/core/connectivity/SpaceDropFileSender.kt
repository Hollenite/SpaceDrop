package com.prototype.airdrop.core.connectivity

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.core.security.IdentityKeyStore
import com.prototype.airdrop.core.security.PairedPeerStore
import org.json.JSONObject
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

class SpaceDropFileSender(
    private val deviceIdStore: DeviceIdStore,
    private val deviceNameProvider: () -> String = { DeviceNameStore.defaultName() },
    private val identity: IdentityKeyStore,
    private val peers: PairedPeerStore
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
        val speedText: String,
        val fileIndex: Int = 0,
        val fileCount: Int = 1,
        val fileName: String = ""
    )

    fun sendFile(
        host: String,
        port: Int,
        item: TransferItem,
        contentResolver: ContentResolver,
        onProgress: (ProgressUpdate) -> Unit,
        expectedDeviceId: String? = null
    ): SendResult = sendFiles(host, port, listOf(item), contentResolver, onProgress, expectedDeviceId)

    fun sendFiles(
        host: String,
        port: Int,
        items: List<TransferItem>,
        contentResolver: ContentResolver,
        onProgress: (ProgressUpdate) -> Unit,
        expectedDeviceId: String? = null
    ): SendResult {
        if (items.isEmpty()) return SendResult(false, error = "No files to send")

        val peer = expectedDeviceId?.let { peers.findByDeviceId(it) }
            ?: return SendResult(false, error = "Not paired with this PC — scan its QR first")

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

        var sslSocket: SSLSocket? = null
        return try {
            identity.loadOrCreate()
            val ctx = identity.createSslContext(setOf(peer.fingerprint))
            val factory = ctx.socketFactory
            sslSocket = factory.createSocket() as SSLSocket
            sslSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            sslSocket.soTimeout = READ_TIMEOUT_MS
            sslSocket.startHandshake()

            val serverCert = sslSocket.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: return SendResult(false, error = "No server certificate")
            val fp = IdentityKeyStore.fingerprintOf(serverCert)
            if (!fp.equals(peer.fingerprint, ignoreCase = true)) {
                return SendResult(false, error = "Server identity mismatch (possible MITM)")
            }

            val output = sslSocket.outputStream
            val reader = sslSocket.inputStream.bufferedReader(Charsets.UTF_8)

            val pingJson = JSONObject().apply {
                put("type", "PING")
                put("device_name", deviceNameProvider())
                put("device_id", deviceIdStore.getOrCreateDeviceId())
                put("count", items.size)
            }
            writeLine(output, pingJson.toString())

            val pongLine = reader.readLine() ?: return SendResult(false, error = "Empty PONG")
            val pong = JSONObject(pongLine)
            if (pong.optString("type") != "PONG" || !pong.optBoolean("ok", true)) {
                return SendResult(false, error = pong.optString("error", "Handshake rejected"))
            }
            val pcName = pong.optString("device_name", peer.displayName)

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
        } catch (error: Exception) {
            Log.d(TAG, "sendFiles failed: ${error.message}")
            SendResult(false, error = "${error.javaClass.simpleName}: ${error.message}")
        } finally {
            runCatching { sslSocket?.close() }
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
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 32 * 1024
    }
}
