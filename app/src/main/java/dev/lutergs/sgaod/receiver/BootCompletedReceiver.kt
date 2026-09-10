package dev.lutergs.sgaod.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.service.AODService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!context.aodStore.settings.enabled || !Settings.canDrawOverlays(context)) return
        try { context.startForegroundService(Intent(context, AODService::class.java)) }
        catch (_: RuntimeException) { context.aodStore.error = context.getString(R.string.restart_required) }
    }
}
