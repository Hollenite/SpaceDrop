package com.prototype.airdrop.ui.screens.share



import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

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

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel

import com.prototype.airdrop.core.connectivity.WifiPermissions

import com.prototype.airdrop.core.connectivity.ble.BlePermissions

import com.prototype.airdrop.core.mock.AppState

import com.prototype.airdrop.core.model.TransferStatus

import com.prototype.airdrop.core.util.FormatUtils

import com.prototype.airdrop.ui.components.EmptyNearbyDevicesState

import com.prototype.airdrop.ui.components.GhostActionButton

import com.prototype.airdrop.ui.components.ShareOrbitalLayout

import kotlinx.coroutines.delay



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun DevicePickerScreen(

    onComplete: () -> Unit,

    onFailed: () -> Unit,

    onBack: () -> Unit,

    viewModel: DevicePickerViewModel = viewModel()

) {

    val rankedDevices by viewModel.rankedDevices.collectAsStateWithLifecycle()

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val session by viewModel.session.collectAsStateWithLifecycle()

    val activeDeviceId by viewModel.activeDeviceId.collectAsStateWithLifecycle()

    val items = viewModel.pendingItems

    val headerText = FormatUtils.shareHeaderText(items)

    var completionHandled by remember { mutableStateOf(false) }

    var showHint by remember { mutableStateOf(true) }



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



    LaunchedEffect(Unit) {

        delay(3_000)

        showHint = false

    }



    LaunchedEffect(AppState.shareRequestId) {

        completionHandled = false

        showHint = true

        viewModel.prepareForNewShare()

        if (BlePermissions.hasAll(context)) {

            viewModel.refresh()

        }

    }



    LaunchedEffect(session?.status, activeDeviceId) {

        if (activeDeviceId == null) return@LaunchedEffect

        when (session?.status) {

            TransferStatus.COMPLETED -> {

                if (!completionHandled) {

                    completionHandled = true

                    viewModel.awaitSentAnimation {

                        onComplete()

                    }

                }

            }

            TransferStatus.FAILED -> {

                if (!completionHandled) {

                    completionHandled = true

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

                            text = "SpaceDrop can send one file at a time in this version. Share a single file and try again.",

                            style = MaterialTheme.typography.bodyMedium,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            modifier = Modifier.padding(top = 8.dp)

                        )

                    }

                }

                isLoading && rankedDevices.isEmpty() -> {

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

                rankedDevices.isEmpty() -> {

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

                    Box(modifier = Modifier.fillMaxSize()) {

                        ShareOrbitalLayout(
                            rankedDevices = rankedDevices,
                            activeDeviceId = activeDeviceId,
                            transferTargetId = session?.targetDevice?.id,
                            transferStatus = session?.status,
                            transferProgress = session?.progressPercent ?: 0,
                            onDeviceClick = { ranked -> viewModel.startTransfer(ranked.device) },
                            modifier = Modifier.fillMaxSize()
                        )



                        AnimatedVisibility(

                            visible = showHint && activeDeviceId == null,

                            enter = fadeIn(),

                            exit = fadeOut(),

                            modifier = Modifier

                                .align(Alignment.TopCenter)

                                .padding(top = 8.dp)

                        ) {

                            Text(

                                text = "Tap a device to send",

                                style = MaterialTheme.typography.labelLarge,

                                color = MaterialTheme.colorScheme.onSurfaceVariant

                            )

                        }



                        if (session?.status == TransferStatus.SENDING) {

                            GhostActionButton(

                                text = "Cancel",

                                onClick = { viewModel.cancelTransfer() },

                                modifier = Modifier

                                    .align(Alignment.BottomCenter)

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


