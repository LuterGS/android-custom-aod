package dev.lutergs.sgaod.domain

fun AodSettings.normalized(): AodSettings = copy(
    brightness = brightness.coerceIn(1, 100), idleMinutes = idleMinutes.coerceIn(0, 120),
    clockScale = clockScale.coerceIn(50, 200), dateScale = dateScale.coerceIn(50, 200),
    periodScale = periodScale.coerceIn(50, 200), layoutScale = layoutScale.coerceIn(50, 200),
    themeColor = themeColor or 0xff000000.toInt(),
    maxNotifications = maxNotifications.coerceIn(0, 10),
    maxPriorityNotifications = maxPriorityNotifications.coerceIn(0, 5),
)

data class NotificationRow(val entry: NotificationEntry, val count: Int)
data class NotificationSection(val priority: Boolean, val rows: List<NotificationRow>,
    val remaining: Int, val detailed: Boolean)

/** Privacy is resolved before this display policy. No selection can restore redacted content. */
object NotificationLayout {
    /** Bound memory separately so a busy priority app cannot evict every regular notification. */
    fun retain(entries: List<NotificationEntry>, settings: AodSettings): List<NotificationEntry> {
        val (priority, regular) = entries.sortedByDescending { it.postedAt }
            .partition { it.packageName in settings.priorityPackages }
        return priority.take(50) + regular.take(50)
    }

    fun sections(entries: List<NotificationEntry>, settings: AodSettings): List<NotificationSection> {
        val visible = entries.filter { it.packageName !in settings.excludedPackages }.sortedByDescending { it.postedAt }
        val (priority, regular) = visible.partition { it.packageName in settings.priorityPackages }
        return listOf(section(priority, true, settings.maxPriorityNotifications.coerceIn(0, 5)),
            section(regular, false, settings.maxNotifications.coerceIn(0, 10))).filter { it.rows.isNotEmpty() }
    }
    private fun section(entries: List<NotificationEntry>, priority: Boolean, limit: Int): NotificationSection {
        val detailed = entries.any { it.title.isNotBlank() || it.text.isNotBlank() }
        val all = if (detailed) entries.map { NotificationRow(it, 1) }
            else entries.groupBy { it.packageName }.values.map { NotificationRow(it.first(), it.size) }
        val rows = all.take(limit)
        return NotificationSection(priority, rows, entries.size - rows.sumOf { it.count }, detailed)
    }
}

object ChargingPresentation {
    // Only explicit charger capability is used; battery-side current/voltage is not charger power.
    fun isFast(charging: Boolean, highVoltage: Boolean, maxMicroamps: Int, maxMicrovolts: Int): Boolean =
        charging && (highVoltage || (maxMicroamps > 0 && maxMicrovolts > 0 &&
            maxMicroamps.toLong() * maxMicrovolts >= 15_000_000_000_000L))
    fun bolts(battery: BatteryState): Int = if (!battery.charging || battery.full) 0 else if (battery.fast) 2 else 1
    fun remainingMinutes(battery: BatteryState, elapsed: Long): Long? {
        if (!battery.charging || battery.full || battery.remainingMillis <= 0) return null
        val age = elapsed - battery.sampledAtElapsed
        if (age < 0 || age > 10 * 60_000L) return null
        val remaining = battery.remainingMillis - age
        if (remaining <= 0) return null
        return remaining / 60_000 + if (remaining % 60_000 > 0) 1 else 0
    }
}
