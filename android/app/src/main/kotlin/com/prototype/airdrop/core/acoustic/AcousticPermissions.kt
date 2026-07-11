package com.prototype.airdrop.core.acoustic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Mirrors [com.prototype.airdrop.core.connectivity.ble.BlePermissions] for the mic. */
object AcousticPermissions {
    fun requiredPermissions(): Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    fun hasAll(context: Context): Boolean =
        requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
}
