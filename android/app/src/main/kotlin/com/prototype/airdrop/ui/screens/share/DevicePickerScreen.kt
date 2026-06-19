package com.prototype.airdrop.ui.screens.share

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prototype.airdrop.core.model.TrustState
import com.prototype.airdrop.core.util.FormatUtils
import com.prototype.airdrop.ui.components.DeviceCard
import com.prototype.airdrop.ui.components.EmptyNearbyDevicesState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerScreen(
    onDeviceSelected: (String) -> Unit,
    onDeviceNew: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DevicePickerViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val items = viewModel.pendingItems
    val headerText = FormatUtils.shareHeaderText(items)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
        if (devices.isEmpty()) {
            EmptyNearbyDevicesState(
                onScanQr = { /* placeholder */ },
                onHowItWorks = { /* placeholder */ },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(top = 32.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = "Choose a device",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                when (device.trustState) {
                                    TrustState.PAIRED -> onDeviceSelected(device.id)
                                    TrustState.NEW_DEVICE -> onDeviceNew(device.id)
                                    TrustState.BLOCKED -> { /* no-op */ }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
