package com.prototype.airdrop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.prototype.airdrop.ui.screens.settings.RoomDiscoveryLabScreen
import com.prototype.airdrop.ui.screens.settings.SavedDevicesScreen
import com.prototype.airdrop.ui.screens.settings.SettingsScreen
import com.prototype.airdrop.ui.screens.share.DevicePickerScreen
import com.prototype.airdrop.ui.screens.transfer.CompleteScreen
import com.prototype.airdrop.ui.screens.transfer.FailedScreen
import com.prototype.airdrop.ui.screens.transfer.SendingScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
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
            DevicePickerScreen(
                onDeviceSelected = { deviceId ->
                    val device = AppDependencies.repository.findById(deviceId)
                    if (device != null) {
                        AppState.selectedDevice = device
                        navController.navigate(Routes.SENDING) {
                            popUpTo(Routes.DEVICE_PICKER)
                        }
                    }
                },
                onDeviceNew = { deviceId ->
                    navController.navigate(Routes.pairing(deviceId, fromShare = true))
                },
                onBack = { navController.popBackStack() }
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
            CompleteScreen(
                onDone = {
                    AppState.clear()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onSendMore = { /* placeholder */ }
            )
        }

        composable(Routes.FAILED) {
            FailedScreen(
                onTryAgain = {
                    navController.navigate(Routes.SENDING) {
                        popUpTo(Routes.FAILED) { inclusive = true }
                    }
                },
                onChooseAnother = {
                    navController.navigate(Routes.DEVICE_PICKER) {
                        popUpTo(Routes.DEVICE_PICKER) { inclusive = false }
                    }
                },
                onCancel = {
                    AppState.clear()
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

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
