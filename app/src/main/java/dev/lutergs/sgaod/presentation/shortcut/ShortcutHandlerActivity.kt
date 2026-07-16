package dev.lutergs.sgaod.presentation.shortcut

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.lutergs.sgaod.di.ApplicationScope
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dev.lutergs.sgaod.service.AODService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App Shortcuts를 처리하는 투명 Activity
 * 삼성 루틴의 "앱 동작 바로 실행"에서 사용됩니다.
 *
 * Theme.NoDisplay 액티비티는 onResume 완료 전에 finish() 를 호출해야 하므로
 * (아니면 IllegalStateException 크래시) 실제 작업은 애플리케이션 스코프에 위임하고
 * onCreate 에서 즉시 종료한다.
 */
@AndroidEntryPoint
class ShortcutHandlerActivity : ComponentActivity() {

    companion object {
        const val ACTION_AOD_ON = "dev.lutergs.sgaod.ACTION_AOD_ON"
        const val ACTION_AOD_OFF = "dev.lutergs.sgaod.ACTION_AOD_OFF"
        const val ACTION_AOD_TOGGLE = "dev.lutergs.sgaod.ACTION_AOD_TOGGLE"
        private const val TAG = "ShortcutHandler"
    }

    @Inject
    lateinit var settingsUseCases: SettingsUseCases

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        if (action == ACTION_AOD_ON || action == ACTION_AOD_OFF || action == ACTION_AOD_TOGGLE) {
            val appContext = applicationContext
            applicationScope.launch {
                try {
                    val newEnabled = when (action) {
                        ACTION_AOD_ON -> true
                        ACTION_AOD_OFF -> false
                        else -> !settingsUseCases.aodEnabled.first()
                    }

                    settingsUseCases.setAodEnabled(newEnabled)

                    val serviceIntent = Intent(appContext, AODService::class.java)
                    if (newEnabled) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.stopService(serviceIntent)
                    }
                    Log.d(TAG, "AOD enabled set to: $newEnabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to control AOD", e)
                }
            }
        } else {
            Log.w(TAG, "Unknown action: $action")
        }

        // Theme.NoDisplay 요건: onResume 전에 반드시 동기적으로 finish
        finish()
    }
}
