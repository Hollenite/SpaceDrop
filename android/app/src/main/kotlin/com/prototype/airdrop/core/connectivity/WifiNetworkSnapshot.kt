package com.prototype.airdrop.core.connectivity



import android.content.Context

import android.net.wifi.WifiManager

import android.os.Build

import android.util.Log



object WifiNetworkSnapshot {



    private const val PLACEHOLDER_BSSID = "02:00:00:00:00:00"



    fun currentBssid(context: Context): String? {

        if (!WifiPermissions.hasAll(context)) {

            Log.d(TAG, "BSSID unavailable: missing ${WifiPermissions.requiredPermissions().joinToString()}")

            return null

        }



        val appContext = context.applicationContext

        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            ?: return null



        if (!wifiManager.isWifiEnabled) {

            Log.d(TAG, "BSSID unavailable: Wi-Fi disabled")

            return null

        }



        @Suppress("DEPRECATION")

        val info = wifiManager.connectionInfo ?: return null

        val raw = info.bssid?.trim()?.lowercase() ?: return null

        if (raw.isBlank() || raw == PLACEHOLDER_BSSID || raw == "00:00:00:00:00:00") {

            Log.d(

                TAG,

                "BSSID unavailable: placeholder or blank (sdk=${Build.VERSION.SDK_INT}, " +

                    "grant nearby_wifi=${WifiPermissions.hasAll(context)})"

            )

            return null

        }

        return normalizeBssid(raw)

    }



    fun bssidMatches(phoneBssid: String?, pcBssid: String?): Boolean {

        val phone = phoneBssid?.let(::normalizeBssid) ?: return false

        val pc = pcBssid?.let(::normalizeBssid) ?: return false

        return phone.isNotBlank() && phone == pc

    }



    private fun normalizeBssid(value: String): String {

        return value.trim().lowercase().replace('-', ':')

    }



    fun logAvailability(context: Context) {

        val bssid = currentBssid(context)

        val granted = WifiPermissions.hasAll(context)

        Log.d(

            TAG,

            "Phone BSSID snapshot: ${bssid ?: "unavailable"} " +

                "(sdk=${Build.VERSION.SDK_INT}, wifiPerm=$granted)"

        )

    }



    private const val TAG = "SpaceDropWifi"

}


