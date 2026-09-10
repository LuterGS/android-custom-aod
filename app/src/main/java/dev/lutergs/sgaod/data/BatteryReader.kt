package dev.lutergs.sgaod.data

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import dev.lutergs.sgaod.domain.BatteryState
import dev.lutergs.sgaod.domain.ChargingPresentation

/** Called only for battery broadcasts; no separate polling or wake-up timer. */
class BatteryReader(context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)
    fun read(intent: Intent): BatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).coerceIn(0, 100).toInt() else -1
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        val charging = plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING
        // Optional Samsung broadcast field, observed on SM-F968N. Missing fields never imply fast charging.
        val highVoltage = Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
            intent.getBooleanExtra("hv_charger", false)
        val fast = ChargingPresentation.isFast(charging, highVoltage,
            intent.getIntExtra("max_charging_current", 0), intent.getIntExtra("max_charging_voltage", 0))
        val remaining = if (charging) runCatching { manager.computeChargeTimeRemaining() }.getOrDefault(-1) else -1
        return BatteryState(percent, plugged, full, charging, fast, remaining, SystemClock.elapsedRealtime())
    }
}
