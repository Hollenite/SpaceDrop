package com.prototype.airdrop.core.security

import android.util.Log
import com.prototype.airdrop.core.connectivity.DeviceIdStore
import com.prototype.airdrop.core.connectivity.DeviceNameStore
import org.json.JSONObject
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

class PairingClient(
    private val identity: IdentityKeyStore,
    private val peers: PairedPeerStore,
    private val deviceIdStore: DeviceIdStore,
    private val deviceNameProvider: () -> String
) {
    data class Result(val success: Boolean, val message: String)

    /**
     * Completes pairing against a PC discovered by QR. [host] comes from BLE/mDNS for that deviceId.
     */
    fun pair(offer: PairingOffer, host: String): Result {
        if (PairingQrParser.isExpired(offer)) {
            return Result(false, "QR expired — refresh it on the PC")
        }
        return try {
            identity.loadOrCreate()
            val ctx = identity.createSslContext(
                trustFingerprints = emptySet(),
                alsoAcceptFingerprint = offer.fingerprint
            )
            val socket = ctx.socketFactory.createSocket() as SSLSocket
            socket.connect(InetSocketAddress(host, offer.port), 10_000)
            socket.soTimeout = 60_000
            socket.startHandshake()

            val serverCert = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: return Result(false, "No server certificate")
            val fp = IdentityKeyStore.fingerprintOf(serverCert)
            if (!fp.equals(offer.fingerprint, ignoreCase = true)) {
                socket.close()
                return Result(false, "Certificate does not match QR (possible MITM)")
            }

            val req = JSONObject()
                .put("type", "PAIR_REQUEST")
                .put("device_id", deviceIdStore.getOrCreateDeviceId())
                .put("device_name", deviceNameProvider())
            val out = socket.outputStream
            out.write((req.toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()

            val line = socket.inputStream.bufferedReader().readLine()
            socket.close()
            if (line.isNullOrBlank()) return Result(false, "Empty PAIR_RESULT")
            val json = JSONObject(line)
            if (!json.optBoolean("ok", false)) {
                return Result(false, json.optString("error", "Pairing declined"))
            }

            peers.upsert(
                PairedPeer(
                    deviceId = offer.deviceId,
                    fingerprint = offer.fingerprint,
                    displayName = json.optString("device_name", "PC"),
                    pairedAtUnix = System.currentTimeMillis() / 1000,
                    alwaysAllow = true
                )
            )
            Result(true, "Paired with ${json.optString("device_name", "PC")}")
        } catch (e: Exception) {
            Log.e(TAG, "pair failed", e)
            Result(false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SpaceDropPair"
    }
}
