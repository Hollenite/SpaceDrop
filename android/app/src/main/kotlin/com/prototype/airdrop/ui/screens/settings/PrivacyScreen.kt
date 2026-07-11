package com.prototype.airdrop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "How SpaceDrop protects your transfers",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                heading = "No cloud, no servers",
                body = "Files travel directly between your phone and your PC on your local network. Nothing is uploaded to any SpaceDrop server."
            )

            PrivacySection(
                heading = "QR pairing",
                body = "You pair by scanning a QR code shown on your PC tray. That QR pins the PC's cryptographic identity so a fake PC on the Wi‑Fi cannot impersonate it."
            )

            PrivacySection(
                heading = "Encrypted transfers",
                body = "After pairing, every file transfer uses mutual TLS. Only previously paired devices can send or receive. Unpaired phones are rejected."
            )

            PrivacySection(
                heading = "Room confirmation tone",
                body = "The PC plays a brief ultrasonic tone only while you are looking for devices (phone-armed). The tone never carries your files."
            )

            PrivacySection(
                heading = "What stays local",
                body = "Pairing keys and peer records stay on each device. File contents are not sent to the cloud."
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacySection(heading: String, body: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = heading,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
