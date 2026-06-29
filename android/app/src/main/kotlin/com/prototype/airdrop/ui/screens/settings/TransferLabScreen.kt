package com.prototype.airdrop.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.ui.components.LiquidTransferProgressBar
import com.prototype.airdrop.ui.components.PrimaryActionButton
import com.prototype.airdrop.ui.components.SecondaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferLabScreen(
    onBack: () -> Unit,
    viewModel: TransferLabViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = uri.lastPathSegment ?: "file"
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        val mime = context.contentResolver.getType(uri)
        viewModel.setSelectedFile(uri, name, size, mime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer lab") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Dev file transfer test",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pick a file, discover a PC, and send over TCP with a liquid progress bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Target PC: ${devices.firstOrNull()?.name ?: if (isLoading) "Discovering…" else "None found"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            devices.firstOrNull()?.host?.let { host ->
                Text(
                    text = "Host: $host",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = viewModel.selectedFileLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryActionButton(
                text = "Choose image",
                onClick = { filePicker.launch(arrayOf("image/*")) },
                enabled = !viewModel.isTransferring,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryActionButton(
                text = "Refresh discovery",
                onClick = { viewModel.refresh() },
                enabled = !viewModel.isTransferring,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            LiquidTransferProgressBar(
                progressPercent = session?.progressPercent ?: 0,
                label = "Idle",
                isComplete = session?.status == TransferStatus.COMPLETED,
                modifier = Modifier.fillMaxWidth()
            )

            session?.speedText?.takeIf { it.isNotEmpty() }?.let { speed ->
                Text(
                    text = "Speed: $speed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            session?.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryActionButton(
                text = when (session?.status) {
                    TransferStatus.SENDING -> "Sending…"
                    TransferStatus.COMPLETED -> "Sent ✓"
                    else -> "Send file"
                },
                onClick = {
                    if (session?.status == TransferStatus.COMPLETED) {
                        viewModel.resetTransfer()
                        return@PrimaryActionButton
                    }
                    if (!viewModel.hasSelectedFile) {
                        Toast.makeText(context, "Choose a file first", Toast.LENGTH_SHORT).show()
                        return@PrimaryActionButton
                    }
                    if (devices.isEmpty()) {
                        Toast.makeText(context, "No PC discovered", Toast.LENGTH_SHORT).show()
                        return@PrimaryActionButton
                    }
                    viewModel.sendToFirstDevice()
                },
                enabled = !viewModel.isTransferring && devices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.isTransferring) {
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryActionButton(
                    text = "Cancel",
                    onClick = { viewModel.cancelTransfer() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
