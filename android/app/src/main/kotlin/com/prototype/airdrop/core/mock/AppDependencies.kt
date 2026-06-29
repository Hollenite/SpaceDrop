package com.prototype.airdrop.core.mock

import android.content.Context
import com.prototype.airdrop.core.transfer.TcpTransferEngine
import com.prototype.airdrop.core.transfer.TransferEngine

/**
 * Singleton holders for prototype dependencies.
 * Mock implementations power Home/Diagnostics; TCP engine powers share flow.
 */
object AppDependencies {
    val mockRepository = MockNearbyDeviceRepository()
    val fakeTransferEngine = FakeTransferEngine(mockRepository)

    /** Alias used by Settings/Diagnostics mock scenarios. */
    val repository get() = mockRepository

    /** Mock transfer engine for Home → Pairing → Sending demo flow. */
    val transferEngine: TransferEngine get() = fakeTransferEngine

    private var tcpTransferEngine: TcpTransferEngine? = null

    fun tcpTransferEngine(context: Context): TcpTransferEngine {
        return tcpTransferEngine ?: TcpTransferEngine(context.applicationContext).also {
            tcpTransferEngine = it
        }
    }
}
