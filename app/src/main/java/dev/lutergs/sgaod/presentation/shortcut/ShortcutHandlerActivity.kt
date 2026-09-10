package dev.lutergs.sgaod.presentation.shortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.presentation.main.MainActivity
import dev.lutergs.sgaod.service.AODService

/** Visible Activity entry point keeps user-triggered shortcuts compatible with FGS start restrictions. */
class ShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val enabled = when (intent.action) {
            "dev.lutergs.sgaod.ACTION_AOD_ON" -> true
            "dev.lutergs.sgaod.ACTION_AOD_OFF" -> false
            "dev.lutergs.sgaod.ACTION_AOD_TOGGLE" -> !aodStore.settings.enabled
            else -> { finish(); return }
        }
        if (enabled && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        aodStore.updateSettings(aodStore.settings.copy(enabled = enabled))
        window.decorView.post {
            if (enabled) {
                try { startForegroundService(Intent(this, AODService::class.java)) }
                catch (_: RuntimeException) { startActivity(Intent(this, MainActivity::class.java)) }
            } else stopService(Intent(this, AODService::class.java))
            finish()
        }
    }
}
