package dev.lutergs.sgaod.domain

/** Android-independent decisions; the service owns lifecycle and effects. */
data class AodSettings(
    val enabled: Boolean = false,
    val pocketDetection: Boolean = true,
    val faceDownDetection: Boolean = true,
    val respectPowerSaver: Boolean = true,
    val notificationTimeFormat: NotificationTimeFormat = NotificationTimeFormat.RELATIVE,
    val showNotificationContent: Boolean = false,
    val brightness: Int = 3,
    val idleMinutes: Int = 30,
    val sleepAtNight: Boolean = false,
    val clockScale: Int = 100,
    val dateScale: Int = 100,
    val periodScale: Int = 100,
    val layoutScale: Int = 100,
    val themeColor: Int = 0xff94b4a5.toInt(),
    val priorityPackages: Set<String> = emptySet(),
    val maxNotifications: Int = 4,
    val maxPriorityNotifications: Int = 3,
    val excludedPackages: Set<String> = emptySet(),
)

enum class SleepReason { NONE, COVERED, FACE_DOWN, POWER_SAVER, LOW_BATTERY, HOT, NIGHT, IDLE, CALL }

data class Environment(
    val covered: Boolean = false,
    val faceDown: Boolean = false,
    val powerSaver: Boolean = false,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val hot: Boolean = false,
    val hour: Int = 12,
    val idle: Boolean = false,
    val inCall: Boolean = false,
)

object AodPolicy {
    fun sleepReason(settings: AodSettings, environment: Environment): SleepReason = with(environment) {
        when {
            inCall -> SleepReason.CALL
            settings.pocketDetection && covered -> SleepReason.COVERED
            settings.faceDownDetection && faceDown -> SleepReason.FACE_DOWN
            hot -> SleepReason.HOT
            settings.respectPowerSaver && powerSaver -> SleepReason.POWER_SAVER
            batteryPercent in 0..15 && !charging -> SleepReason.LOW_BATTERY
            settings.sleepAtNight && (hour >= 23 || hour < 7) -> SleepReason.NIGHT
            idle -> SleepReason.IDLE
            else -> SleepReason.NONE
        }
    }

    // Hysteresis prevents flicker when the device is almost horizontal.
    fun faceDown(z: Float, previous: Boolean): Boolean = if (previous) z < -6f else z < -8f
    fun covered(distance: Float, maximumRange: Float): Boolean =
        distance.isFinite() && distance >= 0f && distance < minOf(maximumRange, 5f)
}

/** Throttles event bursts without a permanent timer; hidden periods schedule nothing. */
class FrameGate(private val intervalMs: Long = 1_000L) {
    private var lastFrame: Long? = null
    fun delay(now: Long): Long = lastFrame?.let { (intervalMs - (now - it)).coerceAtLeast(0) } ?: 0
    fun rendered(now: Long, content: Boolean = true) { if (content) lastFrame = now }
}

data class BatteryState(val percent: Int = -1, val plugged: Int = 0, val full: Boolean = false,
    val charging: Boolean = plugged != 0 && !full, val fast: Boolean = false,
    val remainingMillis: Long = -1, val sampledAtElapsed: Long = 0)
data class MusicState(val title: String = "", val artist: String = "", val playing: Boolean = false)
data class NotificationEntry(val key: String, val packageName: String, val appName: String,
    val title: String, val text: String, val postedAt: Long)
data class AodContent(val battery: BatteryState = BatteryState(),
    val music: MusicState = MusicState(), val notifications: List<NotificationEntry> = emptyList())

/** Secure by default: missing system privacy settings never expose message text. */
object NotificationPrivacy {
    const val SECRET = -1
    const val PRIVATE = 0
    const val PUBLIC = 1
    fun visibility(notification: Int, channel: Int?, ranking: Int?): Int = ranking ?: channel ?: notification
    fun showContent(optIn: Boolean, systemShows: Boolean, allowsPrivate: Boolean, visibility: Int): Boolean =
        optIn && systemShows && visibility != SECRET && (visibility == PUBLIC || allowsPrivate)
}
