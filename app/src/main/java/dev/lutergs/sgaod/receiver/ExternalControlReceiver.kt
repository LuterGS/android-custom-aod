package dev.lutergs.sgaod.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dev.lutergs.sgaod.service.AODService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tasker, 삼성 Routine 등 외부 앱에서 AOD를 제어할 수 있도록 하는 BroadcastReceiver
 *
 * 지원하는 Intent Actions:
 * - dev.lutergs.sgaod.ACTION_AOD_ON: AOD 켜기
 * - dev.lutergs.sgaod.ACTION_AOD_OFF: AOD 끄기
 * - dev.lutergs.sgaod.ACTION_AOD_TOGGLE: AOD 토글
 */
@AndroidEntryPoint
class ExternalControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_AOD_ON = "dev.lutergs.sgaod.ACTION_AOD_ON"
        const val ACTION_AOD_OFF = "dev.lutergs.sgaod.ACTION_AOD_OFF"
        const val ACTION_AOD_TOGGLE = "dev.lutergs.sgaod.ACTION_AOD_TOGGLE"
        private const val TAG = "ExternalControlReceiver"
    }

    @Inject
    lateinit var settingsUseCases: SettingsUseCases

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")

        val action = intent.action
        if (action != ACTION_AOD_ON && action != ACTION_AOD_OFF && action != ACTION_AOD_TOGGLE) {
            Log.w(TAG, "Unknown action: $action")
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newEnabled = when (action) {
                    ACTION_AOD_ON -> true
                    ACTION_AOD_OFF -> false
                    ACTION_AOD_TOGGLE -> !settingsUseCases.aodEnabled.first()
                    else -> return@launch
                }

                // DataStore 설정 업데이트
                settingsUseCases.setAodEnabled(newEnabled)
                Log.d(TAG, "AOD enabled set to: $newEnabled")

                // 서비스 시작/중지
                val serviceIntent = Intent(context, AODService::class.java)
                if (newEnabled) {
                    try {
                        context.startForegroundService(serviceIntent)
                        Log.d(TAG, "AODService started")
                    } catch (e: ForegroundServiceStartNotAllowedException) {
                        // 백그라운드 FGS 시작 제한 (API 31+).
                        // SYSTEM_ALERT_WINDOW 보유 시 면제되므로, 미보유 상태에서만 도달한다.
                        // 설정값은 이미 갱신됐으므로 다음 앱 진입/부팅 시 서비스가 시작된다.
                        Log.w(
                            TAG,
                            "FGS start not allowed from background " +
                                "(overlay permission: ${Settings.canDrawOverlays(context)})",
                            e
                        )
                    }
                } else {
                    context.stopService(serviceIntent)
                    Log.d(TAG, "AODService stopped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to control AOD", e)
            } finally {
                pendingResult.finish()
                Log.d(TAG, "PendingResult finished")
            }
        }
    }
}
