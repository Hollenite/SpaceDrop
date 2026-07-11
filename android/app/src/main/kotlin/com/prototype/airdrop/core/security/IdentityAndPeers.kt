package com.prototype.airdrop.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class PairedPeer(
    val deviceId: String,
    val fingerprint: String,
    val displayName: String,
    val pairedAtUnix: Long,
    val alwaysAllow: Boolean = true
)

class PairedPeerStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<PairedPeer> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PairedPeer(
                        deviceId = o.getString("deviceId"),
                        fingerprint = o.getString("fingerprint"),
                        displayName = o.optString("displayName", "PC"),
                        pairedAtUnix = o.optLong("pairedAtUnix", 0L),
                        alwaysAllow = o.optBoolean("alwaysAllow", true)
                    )
                )
            }
        }
    }

    fun isPairedDeviceId(deviceId: String): Boolean =
        list().any { it.deviceId.equals(deviceId, ignoreCase = true) }

    fun findByDeviceId(deviceId: String): PairedPeer? =
        list().firstOrNull { it.deviceId.equals(deviceId, ignoreCase = true) }

    fun findByFingerprint(fp: String): PairedPeer? =
        list().firstOrNull { it.fingerprint.equals(fp, ignoreCase = true) }

    fun upsert(peer: PairedPeer) {
        val others = list().filterNot {
            it.deviceId.equals(peer.deviceId, ignoreCase = true) ||
                it.fingerprint.equals(peer.fingerprint, ignoreCase = true)
        }
        save(others + peer)
    }

    fun forget(deviceId: String) {
        save(list().filterNot { it.deviceId.equals(deviceId, ignoreCase = true) })
    }

    private fun save(peers: List<PairedPeer>) {
        val arr = JSONArray()
        peers.forEach { p ->
            arr.put(
                JSONObject()
                    .put("deviceId", p.deviceId)
                    .put("fingerprint", p.fingerprint)
                    .put("displayName", p.displayName)
                    .put("pairedAtUnix", p.pairedAtUnix)
                    .put("alwaysAllow", p.alwaysAllow)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "spacedrop_peers"
        private const val KEY = "paired_peers"
    }
}

/**
 * ECDSA P-256 identity + self-signed X.509 cert for mutual TLS.
 */
class IdentityKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val deviceIdStore = com.prototype.airdrop.core.connectivity.DeviceIdStore(appContext)

    lateinit var deviceId: String
        private set
    lateinit var keyPair: KeyPair
        private set
    lateinit var certificate: X509Certificate
        private set
    lateinit var fingerprintHex: String
        private set

    fun loadOrCreate() {
        deviceId = deviceIdStore.getOrCreateDeviceId()
        val certB64 = prefs.getString(KEY_CERT, null)
        val keyB64 = prefs.getString(KEY_PRIV, null)
        if (certB64 != null && keyB64 != null) {
            val certBytes = Base64.decode(certB64, Base64.DEFAULT)
            certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            val privBytes = Base64.decode(keyB64, Base64.DEFAULT)
            val kf = java.security.KeyFactory.getInstance("EC")
            val priv = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))
            keyPair = KeyPair(certificate.publicKey, priv)
        } else {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            keyPair = kpg.generateKeyPair()
            certificate = selfSign(deviceId, keyPair)
            prefs.edit()
                .putString(KEY_CERT, Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
                .putString(
                    KEY_PRIV,
                    Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
                )
                .apply()
        }
        fingerprintHex = fingerprintOf(certificate)
        Log.i(TAG, "Identity ready id=$deviceId fp=${fingerprintHex.take(16)}…")
    }

    fun createSslContext(
        trustFingerprints: Set<String>,
        alsoAcceptFingerprint: String? = null
    ): SSLContext {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setKeyEntry("spacedrop", keyPair.private, charArrayOf(), arrayOf(certificate))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, charArrayOf())

        val allowed = trustFingerprints.map { it.lowercase() }.toMutableSet()
        alsoAcceptFingerprint?.lowercase()?.let { allowed.add(it) }

        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val fp = fingerprintOf(chain[0])
                if (fp !in allowed) {
                    throw java.security.cert.CertificateException("Untrusted server fingerprint $fp")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(certificate)
        }

        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, arrayOf<TrustManager>(tm), SecureRandom())
        return ctx
    }

    companion object {
        private const val TAG = "SpaceDropIdentity"
        private const val PREFS = "spacedrop_identity"
        private const val KEY_CERT = "cert"
        private const val KEY_PRIV = "priv"

        fun fingerprintOf(cert: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun selfSign(deviceId: String, keyPair: KeyPair): X509Certificate {
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 86_400_000L)
            val notAfter = Date(now + 10L * 365 * 86_400_000L)
            val builder = JcaX509v3CertificateBuilder(
                X500Name("CN=SpaceDrop $deviceId"),
                BigInteger(64, SecureRandom()),
                notBefore,
                notAfter,
                X500Name("CN=SpaceDrop $deviceId"),
                keyPair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
            return JcaX509CertificateConverter().getCertificate(builder.build(signer))
        }
    }
}

data class PairingOffer(
    val version: Int,
    val deviceId: String,
    val fingerprint: String,
    val port: Int,
    val nonce: String,
    val exp: Long,
    val signature: String
) {
    fun canonical(): String =
        "v=$version&id=$deviceId&fp=$fingerprint&port=$port&n=$nonce&exp=$exp"
}

object PairingQrParser {
    fun parse(url: String): PairingOffer? {
        if (!url.startsWith("spacedrop://pair?")) return null
        val q = url.substringAfter('?')
        val map = q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return try {
            PairingOffer(
                version = map["v"]?.toInt() ?: return null,
                deviceId = map["id"] ?: return null,
                fingerprint = map["fp"] ?: return null,
                port = map["port"]?.toInt() ?: 58392,
                nonce = map["n"] ?: return null,
                exp = map["exp"]?.toLong() ?: return null,
                signature = map["sig"] ?: return null
            )
        } catch (_: Exception) {
            null
        }
    }

    /** PC signs with ECDSA; phone verifies with cert presented over TLS (fp match), not sig alone.
     *  Still check expiry here. Sig verification requires PC public key from TLS cert after connect.
     */
    fun isExpired(offer: PairingOffer, nowUnix: Long = System.currentTimeMillis() / 1000): Boolean =
        nowUnix > offer.exp
}
