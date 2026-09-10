package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class AodCustomizationTest {
    private fun entry(key: String, pkg: String, time: Long, title: String = "") =
        NotificationEntry(key, pkg, "App $pkg", title, "", time)

    @Test fun oldPriorityNotificationsHaveTheirOwnBudgetAndAreNotDuplicated() {
        val entries = listOf(entry("new", "regular", 10), entry("old", "priority", 1))
        val sections = NotificationLayout.sections(entries, AodSettings(priorityPackages = setOf("priority"),
            maxNotifications = 1, maxPriorityNotifications = 1))
        assertEquals(listOf(true, false), sections.map { it.priority })
        assertEquals(listOf("old", "new"), sections.flatMap { it.rows }.map { it.entry.key })
    }
    @Test fun zeroRegularCountShowsOnlyPriorityAndZeroBothHidesAll() {
        val entries = listOf(entry("a", "p", 1), entry("b", "r", 2))
        val settings = AodSettings(priorityPackages = setOf("p"), maxNotifications = 0)
        assertEquals(listOf("a"), NotificationLayout.sections(entries, settings).flatMap { it.rows }.map { it.entry.key })
        assertTrue(NotificationLayout.sections(entries, settings.copy(maxPriorityNotifications = 0)).isEmpty())
    }
    @Test fun excludedAppsRemainExcludedEvenWhenMarkedPriority() {
        val settings = AodSettings(priorityPackages = setOf("p"), excludedPackages = setOf("p"))
        assertTrue(NotificationLayout.sections(listOf(entry("a", "p", 1)), settings).isEmpty())
    }
    @Test fun redactedNotificationsGroupByAppWithLatestFirstAndCorrectOverflow() {
        val entries = listOf(entry("old", "a", 1), entry("b", "b", 2), entry("new", "a", 3))
        val section = NotificationLayout.sections(entries, AodSettings(maxNotifications = 1)).single()
        assertFalse(section.detailed)
        assertEquals("new", section.rows.single().entry.key)
        assertEquals(2, section.rows.single().count)
        assertEquals(1, section.remaining)
    }
    @Test fun detailedRowsRespectBudgetAndRetainRedactionForPrivateEntries() {
        val entries = listOf(entry("public", "p", 3, "Public"), entry("private", "p", 2), entry("old", "q", 1))
        val section = NotificationLayout.sections(entries, AodSettings(maxNotifications = 2)).single()
        assertTrue(section.detailed)
        assertEquals(listOf("public", "private"), section.rows.map { it.entry.key })
        assertEquals("", section.rows.last().entry.title)
        assertEquals(1, section.remaining)
    }
    @Test fun removingPriorityNotificationCannotResurrectIt() {
        val settings = AodSettings(priorityPackages = setOf("p"))
        assertEquals(1, NotificationLayout.sections(listOf(entry("a", "p", 1)), settings).size)
        assertTrue(NotificationLayout.sections(emptyList(), settings).isEmpty())
    }
    @Test fun storedCustomizationIsBoundedAndColorAlwaysOpaque() {
        val value = AodSettings(clockScale = -1, dateScale = 999, periodScale = 0, layoutScale = 999,
            maxNotifications = -10, maxPriorityNotifications = 99, themeColor = 0x123456).normalized()
        assertEquals(50, value.clockScale); assertEquals(200, value.dateScale)
        assertEquals(50, value.periodScale); assertEquals(200, value.layoutScale)
        assertEquals(0, value.maxNotifications); assertEquals(5, value.maxPriorityNotifications)
        assertEquals(0xff123456.toInt(), value.themeColor)
    }
    @Test fun busyPriorityAppCannotEvictRegularNotificationsAndOlderPrioritySurvivesRegularBurst() {
        val settings = AodSettings(priorityPackages = setOf("p"))
        val burst = (1..100).map { entry("p$it", "p", it.toLong()) }
        val regular = entry("r", "r", 0)
        val retained = NotificationLayout.retain(burst + regular, settings)
        assertEquals(51, retained.size)
        assertTrue(regular in retained)
        assertEquals("p100", retained.first().key)
        val reversed = (1..100).map { entry("r$it", "r", it.toLong()) } + entry("p", "p", 0)
        assertTrue(NotificationLayout.retain(reversed, settings).any { it.key == "p" })
    }
}
