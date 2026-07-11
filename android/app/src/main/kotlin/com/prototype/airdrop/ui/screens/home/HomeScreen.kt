package com.prototype.airdrop.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prototype.airdrop.core.acoustic.AcousticPermissions
import com.prototype.airdrop.core.connectivity.WifiPermissions
import com.prototype.airdrop.core.connectivity.ble.BlePermissions
import com.prototype.airdrop.core.model.TrustState
import com.prototype.airdrop.ui.components.EmptyNearbyDevicesState
import com.prototype.airdrop.ui.components.NearbyDevicesRadar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceNew: (String) -> Unit,
    onSettings: () -> Unit,
    onShowQrPairing: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val rankedDevices by viewModel.rankedDevices.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (!BlePermissions.hasAll(context)) addAll(BlePermissions.requiredPermissions())
            if (!WifiPermissions.hasAll(context)) addAll(WifiPermissions.requiredPermissions())
            if (!AcousticPermissions.hasAll(context)) addAll(AcousticPermissions.requiredPermissions())
        }.distinct().toTypedArray()
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SpaceDrop",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading && rankedDevices.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = "Scanning for nearby PCs…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
                rankedDevices.isEmpty() -> {
                    NearbyDevicesRadar(
                        rankedDevices = emptyList(),
                        onDeviceClick = { },
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.38f)
                    )

                    EmptyNearbyDevicesState(
                        onScanQr = onShowQrPairing,
                        onHowItWorks = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "SpaceDrop finds nearby PCs over Bluetooth + Wi-Fi, then " +
                                        "confirms them by a quiet ultrasonic tone."
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.62f)
                    )

                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    TextButton(
                        onClick = onShowQrPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "No device showing?  Show QR pairing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {
                    NearbyDevicesRadar(
                        rankedDevices = rankedDevices,
                        onDeviceClick = { ranked ->
                            when (ranked.device.trustState) {
                                TrustState.PAIRED -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Use the Share Sheet to send files to ${ranked.device.name}"
                                        )
                                    }
                                }
                                TrustState.NEW_DEVICE -> onDeviceNew(ranked.device.id)
                                TrustState.BLOCKED -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "${ranked.device.name} is blocked"
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    TextButton(
                        onClick = onShowQrPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "No device showing?  Show QR pairing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
