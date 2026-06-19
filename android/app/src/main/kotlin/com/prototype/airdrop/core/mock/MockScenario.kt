package com.prototype.airdrop.core.mock

enum class MockScenario(val displayName: String) {
    PAIRED_DEVICE_NEARBY("Paired device nearby"),
    NEW_DEVICE_ONLY("New device only"),
    NO_DEVICES("No devices"),
    MULTIPLE_DEVICES("Multiple devices"),
    TRANSFER_FAILURE("Transfer failure at ~65%")
}
