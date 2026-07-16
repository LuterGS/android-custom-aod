package dev.lutergs.sgaod.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lutergs.sgaod.presentation.settings.ExcludedAppsScreen
import dev.lutergs.sgaod.presentation.settings.PinnedAppsScreen
import dev.lutergs.sgaod.util.PermissionUtils

enum class ScreenState {
    MAIN, EXCLUDED_APPS, PINNED_APPS
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // rememberSaveable: 화면 회전/프로세스 재생성 시에도 현재 화면 유지
    var screenState by rememberSaveable { mutableStateOf(ScreenState.MAIN) }

    // 개별 권한 상태 관리
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var phoneStateGranted by remember { mutableStateOf(false) }
    var batteryOptGranted by remember { mutableStateOf(false) }
    var postNotificationsGranted by remember { mutableStateOf(false) }

    val permissionsGranted = overlayGranted && notificationGranted &&
            phoneStateGranted && batteryOptGranted && postNotificationsGranted

    // 권한 체크 함수
    fun checkPermissions() {
        overlayGranted = PermissionUtils.hasOverlayPermission(context)
        notificationGranted = PermissionUtils.hasNotificationListenerPermission(context)
        phoneStateGranted = PermissionUtils.hasPhoneStatePermission(context)
        batteryOptGranted = PermissionUtils.isIgnoringBatteryOptimizations(context)
        postNotificationsGranted = PermissionUtils.hasPostNotificationsPermission(context)
    }

    // Lifecycle ON_RESUME 이벤트마다 권한 체크
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 시스템 뒤로가기 버튼 처리 - MAIN이 아닐 때만 활성화
    BackHandler(enabled = screenState != ScreenState.MAIN) {
        screenState = ScreenState.MAIN
    }

    when (screenState) {
        ScreenState.EXCLUDED_APPS -> {
            ExcludedAppsScreen(onBack = { screenState = ScreenState.MAIN })
        }
        ScreenState.PINNED_APPS -> {
            PinnedAppsScreen(
                viewModel = viewModel,
                onBack = { screenState = ScreenState.MAIN }
            )
        }
        ScreenState.MAIN -> {
            if (permissionsGranted) {
                SettingsScreen(
                    viewModel = viewModel,
                    onExcludedAppsClick = { screenState = ScreenState.EXCLUDED_APPS },
                    onPinnedAppsClick = { screenState = ScreenState.PINNED_APPS }
                )
            } else {
                PermissionRequestScreen(
                    overlayGranted = overlayGranted,
                    notificationGranted = notificationGranted,
                    phoneStateGranted = phoneStateGranted,
                    batteryOptGranted = batteryOptGranted,
                    postNotificationsGranted = postNotificationsGranted,
                    onPermissionChanged = { checkPermissions() }
                )
            }
        }
    }
}
