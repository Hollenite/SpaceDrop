package com.prototype.airdrop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.core.util.ContentUriUtils
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.navigation.AppNavGraph
import com.prototype.airdrop.navigation.Routes
import com.prototype.airdrop.ui.theme.PrototypeAirDropTheme

class MainActivity : ComponentActivity() {

    private var shareNavigationTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = applyShareIntent(intent)

        setContent {
            PrototypeAirDropTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        shareNavigationTick = shareNavigationTick,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyShareIntent(intent)
        shareNavigationTick++
    }

    private fun applyShareIntent(intent: Intent): String {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                AppDependencies.tcpTransferEngine(this).reset()
                val items = parseSingleShareIntent(intent)
                AppState.pendingTransferItems = items
                AppState.isFromShareSheet = true
                AppState.shareRequestId++
                Routes.DEVICE_PICKER
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                AppDependencies.tcpTransferEngine(this).reset()
                val items = parseMultipleShareIntent(intent)
                AppState.pendingTransferItems = items
                AppState.isFromShareSheet = true
                AppState.shareRequestId++
                Routes.DEVICE_PICKER
            }
            else -> Routes.ONBOARDING
        }
    }

    private fun parseSingleShareIntent(intent: Intent): List<TransferItem> {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val mimeType = intent.type
        val displayName = uri?.let { ContentUriUtils.resolveDisplayName(this, it, mimeType) } ?: "file"
        val sizeBytes = uri?.let { resolveFileSize(it) }

        return listOf(
            TransferItem(
                contentUri = uri ?: Uri.EMPTY,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes
            )
        )
    }

    private fun parseMultipleShareIntent(intent: Intent): List<TransferItem> {
        val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.filterIsInstance<Uri>() ?: return emptyList()

        return uris.map { uri ->
            TransferItem(
                contentUri = uri,
                displayName = ContentUriUtils.resolveDisplayName(this, uri, intent.type),
                mimeType = intent.type,
                sizeBytes = resolveFileSize(uri)
            )
        }
    }

    private fun resolveFileSize(uri: Uri): Long? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize.takeIf { it > 0 }
            }
        } catch (_: Exception) {
            null
        }
    }
}
