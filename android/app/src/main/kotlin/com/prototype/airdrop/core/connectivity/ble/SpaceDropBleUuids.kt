package com.prototype.airdrop.core.connectivity.ble

import java.util.UUID

object SpaceDropBleUuids {
    val SERVICE: UUID = UUID.fromString("0000a501-0000-1000-8000-00805f9b34fb")
    val DEVICE_ID: UUID = UUID.fromString("0000a502-0000-1000-8000-00805f9b34fb")
    val DEVICE_NAME: UUID = UUID.fromString("0000a503-0000-1000-8000-00805f9b34fb")
    val ENDPOINT: UUID = UUID.fromString("0000a504-0000-1000-8000-00805f9b34fb")
    val CONTROL: UUID = UUID.fromString("0000a505-0000-1000-8000-00805f9b34fb")
}
