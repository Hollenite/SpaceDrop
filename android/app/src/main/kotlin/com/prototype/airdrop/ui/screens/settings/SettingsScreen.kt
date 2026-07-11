package com.prototype.airdrop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prototype.airdrop.BuildConfig
import com.prototype.airdrop.core.connectivity.DeviceNameStore
import com.prototype.airdrop.ui.components.SettingsRow
import com.prototype.airdrop.ui.components.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onSavedDevices: () -> Unit,
    onRoomDiscoveryLab: () -> Unit,
    onBleDiscoveryLab: () -> Unit,
    onTransferLab: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val deviceNameStore = remember { DeviceNameStore(context) }
    val deviceName by deviceNameStore.name.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showAppearsInfo by remember { mutableStateOf(false) }
    var showReceiveInfo by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        var draft by remember(deviceName) { mutableStateOf(deviceName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Device name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Name shown to nearby PCs") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deviceNameStore.setName(draft)
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAppearsInfo) {
        AlertDialog(
            onDismissRequest = { showAppearsInfo = false },
            title = { Text("Appears as") },
            text = {
                Text(
                    "Nearby PCs see this phone as \"$deviceName\". This is the device name above " +
                        "and is sent when you connect. Change it under Device name."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAppearsInfo = false
                    showRenameDialog = true
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showAppearsInfo = false }) { Text("Close") }
            }
        )
    }

    if (showReceiveInfo) {
        AlertDialog(
            onDismissRequest = { showReceiveInfo = false },
            title = { Text("Default receive folder") },
            text = {
                Text(
                    "Received files are saved on the receiving PC (its SpaceDrop/Downloads folder), " +
                        "configured in the Windows tray app. SpaceDrop for Android currently only sends, " +
                        "so there is no receive folder on this phone yet."
                )
            },
            confirmButton = {
                TextButton(onClick = { showReceiveInfo = false }) { Text("Got it") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader(title = "Device")

            SettingsRow(
                title = "Device name",
                subtitle = deviceName,
                icon = Icons.Default.Settings,
                onClick = { showRenameDialog = true }
            )
            SettingsRow(
                title = "Appears as",
                subtitle = "Nearby PCs see \"$deviceName\"",
                icon = Icons.Default.Visibility,
                onClick = { showAppearsInfo = true }
            )

            SettingsSectionHeader(title = "Saved Devices", modifier = Modifier.padding(top = 8.dp))

            SettingsRow(
                title = "Manage paired PCs",
                icon = Icons.Default.Devices,
                onClick = onSavedDevices
            )

            SettingsSectionHeader(title = "Receiving", modifier = Modifier.padding(top = 8.dp))

            SettingsRow(
                title = "Default receive folder",
                subtitle = "Files go to Downloads on your PC",
                icon = Icons.Default.FolderOpen,
                onClick = { showReceiveInfo = true }
            )

            SettingsSectionHeader(title = "Privacy", modifier = Modifier.padding(top = 8.dp))

            SettingsRow(
                title = "How Prototype-airDrop protects transfers",
                icon = Icons.Default.Lock,
                onClick = onPrivacy
            )

            SettingsSectionHeader(title = "Experimental", modifier = Modifier.padding(top = 8.dp))

            SettingsRow(
                title = "Room discovery lab",
                subtitle = "Handshake test",
                icon = Icons.Default.Science,
                onClick = onRoomDiscoveryLab
            )

            if (BuildConfig.DEBUG) {
                SettingsRow(
                    title = "BLE discovery lab",
                    subtitle = "Fused BLE + mDNS + confidence",
                    icon = Icons.Default.Science,
                    onClick = onBleDiscoveryLab
                )

                SettingsRow(
                    title = "Transfer lab",
                    subtitle = "Dev file send + liquid progress",
                    icon = Icons.Default.Upload,
                    onClick = onTransferLab
                )
            }

            SettingsSectionHeader(title = "Diagnostics", modifier = Modifier.padding(top = 8.dp))

            SettingsRow(
                title = "Mock scenarios",
                subtitle = "Switch prototype scenarios",
                icon = Icons.Default.BugReport,
                onClick = onDiagnostics
            )
        }
    }
}
