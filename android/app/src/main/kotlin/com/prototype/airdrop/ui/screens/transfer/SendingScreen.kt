package com.prototype.airdrop.ui.screens.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.ui.components.GhostActionButton
import com.prototype.airdrop.ui.components.TransferProgressCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendingScreen(
    onComplete: () -> Unit,
    onFailed: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TransferViewModel = viewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (session == null) {
            viewModel.startTransfer()
        }
    }

    LaunchedEffect(session?.status) {
        when (session?.status) {
            TransferStatus.COMPLETED -> onComplete()
            TransferStatus.FAILED -> onFailed()
            TransferStatus.CANCELLED -> onCancel()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sending") },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (session == null) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing transfer…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                session?.let { s ->
                    TransferProgressCard(
                        session = s,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                GhostActionButton(
                    text = "Cancel",
                    onClick = {
                        viewModel.cancel()
                        onCancel()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
