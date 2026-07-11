package com.prototype.airdrop.core.connectivity

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted, user-editable display name for this phone. Sent as PING `device_name` so nearby PCs
 * show the chosen name. Defaults to the hardware model.
 */
class DeviceNameStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(load())
    val name: StateFlow<String> = _name.asStateFlow()

    fun getName(): String = _name.value

    fun setName(value: String) {
        val trimmed = value.trim().ifBlank { defaultName() }
        prefs.edit().putString(KEY_DEVICE_NAME, trimmed).apply()
        _name.value = trimmed
    }

    private fun load(): String =
        prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName()

    companion object {
        private const val PREFS_NAME = "spacedrop_prefs"
        private const val KEY_DEVICE_NAME = "device_name"

        fun defaultName(): String = Build.MODEL ?: "Android Device"
    }
}
