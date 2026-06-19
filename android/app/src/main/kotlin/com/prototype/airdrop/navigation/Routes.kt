package com.prototype.airdrop.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DEVICE_PICKER = "device_picker"
    const val PAIRING_ROUTE = "pairing/{deviceId}/{fromShare}"
    const val SENDING = "sending"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val SAVED_DEVICES = "saved_devices"
    const val ROOM_DISCOVERY_LAB = "room_discovery_lab"
    const val DIAGNOSTICS = "diagnostics"

    fun pairing(deviceId: String, fromShare: Boolean = false): String =
        "pairing/$deviceId/$fromShare"
}
