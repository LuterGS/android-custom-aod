package dev.lutergs.sgaod.presentation.main

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import dev.lutergs.sgaod.util.PermissionUtils

@Composable
fun PermissionRequestScreen(
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    phoneStateGranted: Boolean,
    batteryOptGranted: Boolean,
    postNotificationsGranted: Boolean = true,
    onPermissionChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    // Activity Result API — deprecated Activity.requestPermissions 대체.
    // 영구 거부('다시 묻지 않음') 시 시스템 다이얼로그가 뜨지 않으므로 앱 설정으로 폴백한다.
    fun handlePermissionResult(permission: String, granted: Boolean) {
        onPermissionChanged()
        if (!granted) {
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            if (permanentlyDenied) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    )
                } catch (_: Exception) {
                    // 설정 화면 이동 실패 시 무시 (다음 재시도 가능)
                }
            }
        }
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        handlePermissionResult(android.Manifest.permission.READ_PHONE_STATE, granted)
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        handlePermissionResult(android.Manifest.permission.POST_NOTIFICATIONS, granted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("필요한 권한", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "AOD를 표시하려면 아래 권한이 모두 필요합니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            PermissionItem(
                title = "다른 앱 위에 표시",
                description = "잠금화면 위에 AOD를 띄우는 데 필요합니다",
                isGranted = overlayGranted,
                onClick = { PermissionUtils.requestOverlayPermission(context) }
            )

            PermissionItem(
                title = "알림 접근",
                description = "AOD에 알림과 미디어 정보를 표시합니다",
                isGranted = notificationGranted,
                onClick = { PermissionUtils.requestNotificationListenerPermission(context) }
            )

            PermissionItem(
                title = "전화 상태",
                description = "통화 중에는 AOD를 표시하지 않습니다",
                isGranted = phoneStateGranted,
                onClick = {
                    phoneStatePermissionLauncher.launch(
                        android.Manifest.permission.READ_PHONE_STATE
                    )
                }
            )

            PermissionItem(
                title = "알림 표시",
                description = "AOD 서비스 상태 알림을 표시합니다",
                isGranted = postNotificationsGranted,
                onClick = {
                    postNotificationsLauncher.launch(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            )

            PermissionItem(
                title = "배터리 최적화 제외",
                description = "백그라운드에서 AOD 서비스를 유지합니다",
                isGranted = batteryOptGranted,
                onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "권한을 부여하면 자동으로 업데이트됩니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            FilledTonalButton(onClick = onClick, enabled = !isGranted) {
                Text(if (isGranted) "허용됨" else "허용")
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f))
    )
}
