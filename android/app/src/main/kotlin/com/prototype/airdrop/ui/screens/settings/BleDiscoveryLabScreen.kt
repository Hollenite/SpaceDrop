package com.prototype.airdrop.ui.screens.settings



import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.AssistChip

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

import androidx.compose.material3.TopAppBar

import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel

import com.prototype.airdrop.core.connectivity.WifiPermissions

import com.prototype.airdrop.core.connectivity.ble.BlePermissions

import com.prototype.airdrop.ui.components.PrimaryActionButton

import com.prototype.airdrop.ui.theme.SpaceDropColors



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

@Composable

fun BleDiscoveryLabScreen(

    onBack: () -> Unit,

    viewModel: BleDiscoveryLabViewModel = viewModel()

) {

    val devices by viewModel.devices.collectAsStateWithLifecycle()

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val fusionBreakdown by viewModel.fusionBreakdown.collectAsStateWithLifecycle()

    val phoneBssid by viewModel.phoneBssid.collectAsStateWithLifecycle()

    val logLines by viewModel.logLines.collectAsStateWithLifecycle()

    val context = LocalContext.current



    val permissionLauncher = rememberLauncherForActivityResult(

        ActivityResultContracts.RequestMultiplePermissions()

    ) { _ ->

        viewModel.refresh()

    }



    LaunchedEffect(Unit) {

        val needed = buildList {

            if (!BlePermissions.hasAll(context)) addAll(BlePermissions.requiredPermissions())

            if (!WifiPermissions.hasAll(context)) addAll(WifiPermissions.requiredPermissions())

        }.distinct().toTypedArray()

        if (needed.isEmpty()) {

            viewModel.refresh()

        } else {

            permissionLauncher.launch(needed)

        }

    }



    Scaffold(

        topBar = {

            TopAppBar(

                title = { Text("BLE discovery lab") },

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

                .padding(16.dp)

                .verticalScroll(rememberScrollState()),

            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {

            Text(

                text = "DEBUG — fused BLE + mDNS scan with confidence breakdown",

                style = MaterialTheme.typography.bodyMedium,

                color = MaterialTheme.colorScheme.onSurfaceVariant

            )



            Text(

                text = "Phone BSSID: ${phoneBssid ?: "unavailable (grant NEARBY_WIFI_DEVICES)"}",

                style = MaterialTheme.typography.bodySmall,

                color = MaterialTheme.colorScheme.onSurfaceVariant

            )



            PrimaryActionButton(

                text = if (isLoading) "Scanning…" else "Scan again",

                onClick = { viewModel.refresh() },

                enabled = !isLoading,

                modifier = Modifier.fillMaxWidth()

            )



            if (isLoading) {

                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))

            }



            errorMessage?.let { message ->

                Text(

                    text = message,

                    color = MaterialTheme.colorScheme.error,

                    style = MaterialTheme.typography.bodySmall

                )

            }



            Text(

                text = "Devices (${devices.size})",

                style = MaterialTheme.typography.titleSmall

            )



            fusionBreakdown.forEach { (device, fusion) ->

                Column(modifier = Modifier.padding(bottom = 8.dp)) {

                    Text(

                        text = "${device.name} (${device.id.take(8)}…)",

                        style = MaterialTheme.typography.bodyMedium

                    )

                    Text(

                        text = "host=${device.host}:${device.port}",

                        style = MaterialTheme.typography.bodySmall

                    )

                    Text(

                        text = "confidence=${device.confidence} proximity=${device.proximityState}",

                        style = MaterialTheme.typography.bodySmall

                    )



                    FlowRow(

                        horizontalArrangement = Arrangement.spacedBy(6.dp),

                        modifier = Modifier.padding(vertical = 4.dp)

                    ) {

                        SignalChip("mDNS", fusion.hasMdns, SpaceDropColors.Primary)

                        SignalChip("BLE", fusion.hasBle, SpaceDropColors.Primary)

                        SignalChip("WiFi", fusion.bssidMatch, SpaceDropColors.Success)

                    }



                    if (!fusion.bssidMatch) {

                        Text(

                            text = "phone=${fusion.phoneBssid ?: phoneBssid ?: "—"}  pc=${fusion.pcBssid ?: "—"}",

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                    }



                    Text(

                        text = buildString {

                            append("ble=${fusion.hasBle} mdns=${fusion.hasMdns} rssi=${fusion.rssi}")

                            appendLine()

                            append("wifiMatch=${fusion.bssidMatch} signals=${fusion.signalSummary}")

                            appendLine()

                            append(

                                "wBle=${fusion.bleRssiWeight} wMdns=${fusion.mdnsWeight} " +

                                    "wAgree=${fusion.agreementWeight} wBssid=${fusion.bssidWeight} " +

                                    "wHost=${fusion.hostWeight}"

                            )

                        },

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                }

            }



            Text(

                text = "Log",

                style = MaterialTheme.typography.titleSmall

            )



            logLines.forEach { line ->

                Text(

                    text = line,

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            }

        }

    }

}



@Composable

private fun SignalChip(label: String, active: Boolean, activeColor: Color) {

    AssistChip(

        onClick = {},

        label = { Text(label) },

        enabled = false,

        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(

            disabledContainerColor = if (active) activeColor.copy(alpha = 0.25f) else Color.Transparent,

            disabledLabelColor = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant

        )

    )

}


