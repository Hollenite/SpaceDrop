package com.prototype.airdrop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prototype.airdrop.core.security.PairedPeer
import com.prototype.airdrop.core.security.PairedPeerStore
import com.prototype.airdrop.ui.components.SettingsRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDevicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PairedPeerStore(context) }
    var peers by remember { mutableStateOf(store.list()) }
    var forgetTarget by remember { mutableStateOf<PairedPeer?>(null) }

    forgetTarget?.let { peer ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("Forget ${peer.displayName}?") },
            text = {
                Text("This removes the pairing. You will need to scan the PC QR again to send files.")
            },
            confirmButton = {
                TextButton(onClick = {
                    store.forget(peer.deviceId)
                    peers = store.list()
                    forgetTarget = null
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { forgetTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paired PCs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (peers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text(
                    text = "No paired PCs yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "On the PC tray choose Pair phone, then scan the QR from Home.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(peers, key = { it.deviceId }) { peer ->
                    SettingsRow(
                        title = peer.displayName,
                        subtitle = "${peer.deviceId.take(8)}… · tap to forget",
                        icon = Icons.Default.Computer,
                        onClick = { forgetTarget = peer },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
