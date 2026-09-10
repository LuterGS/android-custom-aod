package dev.lutergs.sgaod.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class NotificationTimeFormat { RELATIVE, ABSOLUTE }

object NotificationTime {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    fun minutesAgo(postedAt: Long, now: Long): Long? {
        if (postedAt <= 0) return null
        // A wall-clock correction must not produce a negative age.
        return if (now <= postedAt) 0 else (now - postedAt) / 60_000L
    }
    fun absolute(postedAt: Long, zone: ZoneId): String =
        if (postedAt <= 0) "—" else clock.withZone(zone).format(Instant.ofEpochMilli(postedAt))
}
