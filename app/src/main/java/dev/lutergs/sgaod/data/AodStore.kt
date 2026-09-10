package dev.lutergs.sgaod.data

import android.content.Context
import dev.lutergs.sgaod.domain.*

/** Main-thread state shared by platform adapters and presentation. No polling or retained Activities. */
class AodStore(context: Context) {
    private val preferences = context.getSharedPreferences("aod_v2", Context.MODE_PRIVATE)
    var settings = AodSettings(
        enabled = preferences.getBoolean("enabled", false),
        pocketDetection = preferences.getBoolean("pocket", true),
        faceDownDetection = preferences.getBoolean("face_down", true),
        respectPowerSaver = preferences.getBoolean("power_saver", true),
        showNotificationContent = preferences.getBoolean("content", false),
        brightness = preferences.getInt("brightness", 3).coerceIn(1, 10),
        idleMinutes = preferences.getInt("idle", 30).coerceIn(0, 120),
        sleepAtNight = preferences.getBoolean("night", false),
        excludedPackages = preferences.getStringSet("excluded", emptySet())!!.toSet(),
    )
        private set
    var content = AodContent()
        private set
    var session = false
        private set
    var sleepReason = SleepReason.NONE
        private set
    var settingsScreenVisible = false
    var submittedFrames = 0L
    var lastFrameUptime = 0L
    var serviceRunning = false
    var listenerConnected = false
    var error: String? = null
    val observers = linkedSetOf<() -> Unit>()
    var refreshNotifications: (() -> Unit)? = null
    var dismissSession: (() -> Unit)? = null
    var activityStarted: (() -> Unit)? = null
    var activityStopped: (() -> Unit)? = null
    var sessionId: Long = 0
        private set

    fun updateSettings(value: AodSettings) {
        if (settings == value) return
        settings = value
        preferences.edit().putBoolean("enabled", value.enabled)
            .putBoolean("pocket", value.pocketDetection).putBoolean("face_down", value.faceDownDetection)
            .putBoolean("power_saver", value.respectPowerSaver).putBoolean("content", value.showNotificationContent)
            .putInt("brightness", value.brightness).putInt("idle", value.idleMinutes)
            .putBoolean("night", value.sleepAtNight).putStringSet("excluded", value.excludedPackages).apply()
        refreshNotifications?.invoke()
        notifyChanged()
    }
    fun updateContent(value: AodContent) {
        if (content == value) return
        content = value
        notifyChanged()
    }
    fun setSession(active: Boolean, reason: SleepReason = SleepReason.NONE) {
        if (session == active && sleepReason == reason) return
        if (active && !session) sessionId++
        session = active
        sleepReason = reason
        notifyChanged()
    }
    fun notifyChanged() { observers.toList().forEach { it() } }
}
