package com.prototype.airdrop.ui.components

import android.content.Context
import android.provider.Settings

object MotionPreferences {

    fun isReduceMotionEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
