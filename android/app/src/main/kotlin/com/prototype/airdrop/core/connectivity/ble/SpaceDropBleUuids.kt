package com.prototype.airdrop.core.connectivity.ble

import java.util.UUID

object SpaceDropBleUuids {
    val SERVICE: UUID = UUID.fromString("0000sd01-0000-1000-8000-00805f9b34fb")
    val DEVICE_ID: UUID = UUID.fromString("0000sd02-0000-1000-8000-00805f9b34fb")
    val DEVICE_NAME: UUID = UUID.fromString("0000sd03-0000-1000-8000-00805f9b34fb")
    val ENDPOINT: UUID = UUID.fromString("0000sd04-0000-1000-8000-00805f9b34fb")
}
