package com.prototype.airdrop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.prototype.airdrop.core.mock.AppDependencies
import com.prototype.airdrop.core.mock.AppState
import com.prototype.airdrop.ui.screens.home.HomeScreen
import com.prototype.airdrop.ui.screens.onboarding.OnboardingScreen
import com.prototype.airdrop.ui.screens.pairing.PairingScreen
import com.prototype.airdrop.ui.screens.settings.DiagnosticsScreen
import com.prototype.airdrop.ui.screens.settings.PrivacyScreen
import com.prototype.airdrop.ui.screens.settings.BleDiscoveryLabScreen
import com.prototype.airdrop.ui.screens.settings.RoomDiscoveryLabScreen
import com.prototype.airdrop.ui.screens.settings.SavedDevicesScreen
import com.prototype.airdrop.ui.screens.settings.SettingsScreen
import com.prototype.airdrop.ui.screens.settings.TransferLabScreen
import com.prototype.airdrop.ui.screens.share.DevicePickerScreen
import com.prototype.airdrop.ui.screens.transfer.CompleteScreen
import com.prototype.airdrop.ui.screens.transfer.FailedScreen
import com.prototype.airdrop.ui.screens.transfer.SendingScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    shareNavigationTick: Int = 0,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(shareNavigationTick) {
        if (shareNavigationTick > 0 && AppState.isFromShareSheet) {
            navController.navigate(Routes.DEVICE_PICKER) {
                popUpTo(Routes.DEVICE_PICKER) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onContinue = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onDeviceNew = { deviceId ->
                    navController.navigate(Routes.pairing(deviceId, fromShare = false))
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onShowQrPairing = { /* QR fallback placeholder */ }
            )
        }

        composable(Routes.DEVICE_PICKER) {
            val context = LocalContext.current
            DevicePickerScreen(
                onComplete = {
                    navController.navigate(Routes.COMPLETE) {
                        popUpTo(Routes.DEVICE_PICKER) { inclusive = false }
                    }
                },
                onFailed = {
                    navController.navigate(Routes.FAILED) {
                        popUpTo(Routes.DEVICE_PICKER) { inclusive = false }
                    }
                },
                onBack = {
                    AppState.clear()
                    AppDependencies.tcpTransferEngine(context).reset()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.PAIRING_ROUTE,
            arguments = listOf(
                navArgument("deviceId") { type = NavType.StringType },
                navArgument("fromShare") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val fromShare = backStackEntry.arguments?.getBoolean("fromShare") ?: false
            PairingScreen(
                deviceId = deviceId,
                fromShare = fromShare,
                onPaired = {
                    if (fromShare) {
                        navController.navigate(Routes.SENDING) {
                            popUpTo(Routes.DEVICE_PICKER)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.SENDING) {
            SendingScreen(
                onComplete = {
                    navController.navigate(Routes.COMPLETE) {
                        popUpTo(Routes.SENDING) { inclusive = true }
                    }
                },
                onFailed = {
                    navController.navigate(Routes.FAILED) {
                        popUpTo(Routes.SENDING) { inclusive = true }
                    }
                },
                onCancel = {
                    AppState.clear()
                    AppDependencies.transferEngine.reset()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.COMPLETE) {
            val context = LocalContext.current
            CompleteScreen(
                onDone = {
                    AppState.clear()
                    AppDependencies.transferEngine.reset()
                    AppDependencies.tcpTransferEngine(context).reset()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onSendMore = { /* placeholder */ }
            )
        }

        composable(Routes.FAILED) {
            val context = LocalContext.current
            FailedScreen(
                onTryAgain = {
                    AppDependencies.tcpTransferEngine(context).reset()
                    val destination = if (AppState.isFromShareSheet) {
                        Routes.DEVICE_PICKER
                    } else {
                        Routes.SENDING
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.FAILED) { inclusive = true }
                    }
                },
                onChooseAnother = {
                    AppDependencies.tcpTransferEngine(context).reset()
                    val destination = if (AppState.isFromShareSheet) {
                        Routes.DEVICE_PICKER
                    } else {
                        Routes.HOME
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.FAILED) { inclusive = true }
                    }
                },
                onCancel = {
                    AppState.clear()
                    AppDependencies.tcpTransferEngine(context).reset()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
                onSavedDevices = { navController.navigate(Routes.SAVED_DEVICES) },
                onRoomDiscoveryLab = { navController.navigate(Routes.ROOM_DISCOVERY_LAB) },
                onBleDiscoveryLab = { navController.navigate(Routes.BLE_DISCOVERY_LAB) },
                onTransferLab = { navController.navigate(Routes.TRANSFER_LAB) },
                onDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) }
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SAVED_DEVICES) {
            SavedDevicesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ROOM_DISCOVERY_LAB) {
            RoomDiscoveryLabScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BLE_DISCOVERY_LAB) {
            BleDiscoveryLabScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TRANSFER_LAB) {
            TransferLabScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
