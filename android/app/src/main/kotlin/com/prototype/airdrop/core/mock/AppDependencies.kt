package com.prototype.airdrop.core.mock

import android.content.Context
import com.prototype.airdrop.core.acoustic.AcousticListener
import com.prototype.airdrop.core.connectivity.ble.BlePcScanner
import com.prototype.airdrop.core.discovery.FusedNearbyDeviceRepository
import com.prototype.airdrop.core.transfer.TcpTransferEngine
import com.prototype.airdrop.core.transfer.TransferEngine

/**
 * Singleton holders for prototype dependencies.
 * Mock implementations power Diagnostics; fused discovery powers Home + share flow.
 */
object AppDependencies {
    val mockRepository = MockNearbyDeviceRepository()
    val fakeTransferEngine = FakeTransferEngine(mockRepository)

    /** Alias used by Settings/Diagnostics mock scenarios. */
    val repository get() = mockRepository

    /** Mock transfer engine for Home → Pairing → Sending demo flow. */
    val transferEngine: TransferEngine get() = fakeTransferEngine

    private var tcpTransferEngine: TcpTransferEngine? = null
    private var blePcScanner: BlePcScanner? = null
    private var acousticListener: AcousticListener? = null
    private var fusedNearbyDeviceRepository: FusedNearbyDeviceRepository? = null

    fun tcpTransferEngine(context: Context): TcpTransferEngine {
        return tcpTransferEngine ?: TcpTransferEngine(context.applicationContext).also {
            tcpTransferEngine = it
        }
    }

    fun blePcScanner(context: Context): BlePcScanner {
        return blePcScanner ?: BlePcScanner(context.applicationContext).also {
            blePcScanner = it
        }
    }

    fun acousticListener(context: Context): AcousticListener {
        val appContext = context.applicationContext
        return acousticListener ?: AcousticListener(appContext).also {
            acousticListener = it
        }
    }

    fun fusedNearbyDeviceRepository(context: Context): FusedNearbyDeviceRepository {
        val appContext = context.applicationContext
        return fusedNearbyDeviceRepository ?: FusedNearbyDeviceRepository(
            appContext,
            blePcScanner(appContext),
            acousticListener(appContext)
        ).also {
            fusedNearbyDeviceRepository = it
        }
    }
}
