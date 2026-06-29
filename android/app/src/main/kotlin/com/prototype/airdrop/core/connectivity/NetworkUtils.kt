package com.prototype.airdrop.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient.Companion.HANDSHAKE_PORT

object NetworkUtils {

    private val IPV4_REGEX = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")

    fun isValidIpv4(ip: String): Boolean {
        if (!IPV4_REGEX.matches(ip)) return false
        return ip.split('.').all { octet ->
            val value = octet.toIntOrNull() ?: return false
            value in 0..255
        }
    }

    /**
     * Parses manual connect input. Accepts bare IPv4 or `host:port`.
     * Returns null when the host portion is not a valid IPv4 address.
     */
    fun parseHostAndPort(input: String, defaultPort: Int = HANDSHAKE_PORT): Pair<String, Int>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val host: String
        val port: Int

        if (trimmed.contains(':')) {
            val lastColon = trimmed.lastIndexOf(':')
            host = trimmed.substring(0, lastColon).trim()
            val portText = trimmed.substring(lastColon + 1).trim()
            port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else {
            host = trimmed
            port = defaultPort
        }

        if (!isValidIpv4(host)) return null
        return host to port
    }

    fun isTailscaleOrCgnat(ip: String): Boolean {
        if (!isValidIpv4(ip)) return false
        val first = ip.substringBefore('.').toIntOrNull() ?: return false
        val second = ip.substringAfter('.').substringBefore('.').toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    fun isOnWifi(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
