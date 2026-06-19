package com.prototype.airdrop.core.mock

/**
 * Singleton holders for prototype mock dependencies.
 * In a real app these would be injected via a DI framework.
 */
object AppDependencies {
    val repository = MockNearbyDeviceRepository()
    val transferEngine = FakeTransferEngine(repository)
}
