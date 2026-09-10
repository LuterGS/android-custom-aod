package dev.lutergs.sgaod.data

import android.content.Context
import android.graphics.drawable.Icon
import dev.lutergs.sgaod.domain.*

/** Main-thread state shared by platform adapters and presentation. No polling or retained Activities. */
class AodStore(context: Context) {
    private val preferences = context.getSharedPreferences("aod_v2", Context.MODE_PRIVATE)
    var settings = AodSettings(
        enabled = preferences.getBoolean("enabled", false),
        pocketDetection = preferences.getBoolean("pocket", true),
        faceDownDetection = preferences.getBoolean("face_down", true),
        respectPowerSaver = preferences.getBoolean("power_saver", true),
        notificationTimeFormat = NotificationTimeFormat.entries.firstOrNull {
            it.name == preferences.getString("notification_time_format", null)
        } ?: NotificationTimeFormat.RELATIVE,
        showNotificationContent = preferences.getBoolean("content", false),
        brightness = preferences.getInt("brightness", 3).coerceIn(1, 100),
        idleMinutes = preferences.getInt("idle", 30).coerceIn(0, 120),
        sleepAtNight = preferences.getBoolean("night", false),
        clockScale = preferences.getInt("clock_scale", 100),
        dateScale = preferences.getInt("date_scale", 100),
        periodScale = preferences.getInt("period_scale", 100),
        layoutScale = preferences.getInt("layout_scale", 100),
        themeColor = preferences.getInt("theme_color", 0xff94b4a5.toInt()),
        priorityPackages = preferences.getStringSet("priority", emptySet())!!.toSet(),
        maxNotifications = preferences.getInt("max_notifications", 4),
        maxPriorityNotifications = preferences.getInt("max_priority", 3),
        excludedPackages = preferences.getStringSet("excluded", emptySet())!!.toSet(),
    ).normalized()
        private set
    var content = AodContent()
        private set
    var notificationIcons: Map<String, Icon> = emptyMap()
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

    fun updateSettings(requested: AodSettings) {
        val value = requested.normalized()
        if (settings == value) return
        settings = value
        preferences.edit().putBoolean("enabled", value.enabled)
            .putBoolean("pocket", value.pocketDetection).putBoolean("face_down", value.faceDownDetection)
            .putString("notification_time_format", value.notificationTimeFormat.name)
            .putBoolean("power_saver", value.respectPowerSaver).putBoolean("content", value.showNotificationContent)
            .putInt("brightness", value.brightness).putInt("idle", value.idleMinutes)
            .putInt("clock_scale", value.clockScale).putInt("date_scale", value.dateScale)
            .putInt("period_scale", value.periodScale).putInt("layout_scale", value.layoutScale)
            .putInt("theme_color", value.themeColor).putStringSet("priority", value.priorityPackages)
            .putInt("max_notifications", value.maxNotifications).putInt("max_priority", value.maxPriorityNotifications)
            .putBoolean("night", value.sleepAtNight).putStringSet("excluded", value.excludedPackages).apply()
        refreshNotifications?.invoke()
        notifyChanged()
    }
    fun updateContent(value: AodContent) {
        if (content == value) return
        content = value
        notificationIcons = notificationIcons.filterKeys { key -> value.notifications.any { it.key == key } }
        notifyChanged()
    }
    fun updateNotifications(entries: List<NotificationEntry>, icons: Map<String, Icon>) {
        val changed = content.notifications != entries || notificationIcons != icons
        notificationIcons = icons
        content = content.copy(notifications = entries)
        if (changed) notifyChanged()
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
