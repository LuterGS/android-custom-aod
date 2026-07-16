package dev.lutergs.sgaod.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dev.lutergs.sgaod.service.AODService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsUseCases: SettingsUseCases

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootCompletedReceiver", "Received: ${intent.action}")

        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val aodEnabled = settingsUseCases.aodEnabled.first()
                Log.d("BootCompletedReceiver", "AOD enabled setting: $aodEnabled")

                if (aodEnabled) {
                    val serviceIntent = Intent(context, AODService::class.java)
                    context.startForegroundService(serviceIntent)
                    Log.d("BootCompletedReceiver", "AODService started successfully")
                }
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "Failed to start AODService", e)
            } finally {
                pendingResult.finish()
                Log.d("BootCompletedReceiver", "PendingResult finished")
            }
        }
    }
}
