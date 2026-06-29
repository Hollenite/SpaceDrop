package com.prototype.airdrop.ui.screens.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.prototype.airdrop.core.connectivity.ble.BlePermissions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.core.util.FormatUtils
import com.prototype.airdrop.ui.components.AirDropDeviceAvatar
import com.prototype.airdrop.ui.components.AvatarTransferState
import com.prototype.airdrop.ui.components.EmptyNearbyDevicesState
import com.prototype.airdrop.ui.components.GhostActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerScreen(
    onComplete: () -> Unit,
    onFailed: () -> Unit,
    onBack: () -> Unit,
    viewModel: DevicePickerViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val items = viewModel.pendingItems
    val headerText = FormatUtils.shareHeaderText(items)
    var completionHandled by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (!BlePermissions.hasAll(context)) {
            permissionLauncher.launch(BlePermissions.requiredPermissions())
        }
    }

    LaunchedEffect(session?.status) {
        when (session?.status) {
            TransferStatus.COMPLETED -> {
                if (!completionHandled) {
                    completionHandled = true
                    viewModel.awaitSentAnimation {
                        viewModel.resetTransfer()
                        onComplete()
                    }
                }
            }
            TransferStatus.FAILED -> {
                if (!completionHandled) {
                    completionHandled = true
                    viewModel.resetTransfer()
                    onFailed()
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
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
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                viewModel.multiFileWarning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "One file at a time",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "SpaceDrop can send a single image in this version. Share one file and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                isLoading && devices.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(
                                text = "Looking for nearby PCs…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
                devices.isEmpty() -> {
                    EmptyNearbyDevicesState(
                        onScanQr = { viewModel.refresh() },
                        onHowItWorks = { /* placeholder */ },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 32.dp)
                    )
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "Tap a device to send",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(devices, key = { it.id }) { device ->
                                val progress = if (session?.targetDevice?.id == device.id) {
                                    session?.progressPercent ?: 0
                                } else {
                                    0
                                }
                                val state = viewModel.avatarState(device.id, progress)

                                AirDropDeviceAvatar(
                                    deviceName = device.name,
                                    state = state,
                                    progressPercent = progress,
                                    enabled = viewModel.isDeviceEnabled(device.id) &&
                                        state == AvatarTransferState.Idle,
                                    onClick = { viewModel.startTransfer(device) }
                                )
                            }
                        }

                        if (session?.status == TransferStatus.SENDING) {
                            GhostActionButton(
                                text = "Cancel",
                                onClick = { viewModel.cancelTransfer() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
