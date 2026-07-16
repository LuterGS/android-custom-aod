package dev.lutergs.sgaod.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

object PermissionUtils {

    private const val TAG = "PermissionUtils"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(context, intent)
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasNotificationListenerPermission(context: Context): Boolean {
        // 공식 API 사용 — 문자열 부분 매칭(contains)은 다른 패키지명과 오탐 가능
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun hasPhoneStatePermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestOverlayPermission(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(context, intent)
    }

    fun requestNotificationListenerPermission(context: Context): Boolean {
        // 가능하면 이 앱의 리스너 설정 화면으로 바로 이동
        val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(
                    context,
                    "dev.lutergs.sgaod.service.AODNotificationListener"
                ).flattenToString()
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (startActivitySafely(context, detailIntent, logFailure = false)) return true

        val listIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivitySafely(context, listIntent)
    }

    /**
     * 일부 OEM/기기에는 특정 설정 화면이 없어 ActivityNotFoundException 이 발생할 수 있다.
     * 실패 시 앱 상세 설정 화면으로 폴백한다.
     */
    private fun startActivitySafely(
        context: Context,
        intent: Intent,
        logFailure: Boolean = true
    ): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            if (logFailure) {
                Log.w(TAG, "Settings activity not found: ${intent.action}, falling back", e)
                fallbackToAppDetails(context)
            }
            false
        } catch (e: Exception) {
            if (logFailure) {
                Log.e(TAG, "Failed to start settings activity: ${intent.action}", e)
            }
            false
        }
    }

    private fun fallbackToAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings", e)
        }
    }
}
