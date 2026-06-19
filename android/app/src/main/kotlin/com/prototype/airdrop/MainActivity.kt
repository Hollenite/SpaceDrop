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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.core.model.TransferItem
import com.prototype.airdrop.navigation.AppNavGraph
import com.prototype.airdrop.navigation.Routes
import com.prototype.airdrop.ui.theme.PrototypeAirDropTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = resolveStartDestination(intent)

        setContent {
            PrototypeAirDropTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveStartDestination(intent)
    }

    private fun resolveStartDestination(intent: Intent): String {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val items = parseSingleShareIntent(intent)
                AppState.pendingTransferItems = items
                AppState.isFromShareSheet = true
                Routes.DEVICE_PICKER
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val items = parseMultipleShareIntent(intent)
                AppState.pendingTransferItems = items
                AppState.isFromShareSheet = true
                Routes.DEVICE_PICKER
            }
            else -> Routes.ONBOARDING
        }
    }

    private fun parseSingleShareIntent(intent: Intent): List<TransferItem> {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val mimeType = intent.type
        val displayName = uri?.lastPathSegment ?: "file"
        val sizeBytes = uri?.let { resolveFileSize(it) }

        return listOf(
            TransferItem(
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
                displayName = uri.lastPathSegment ?: "file",
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
