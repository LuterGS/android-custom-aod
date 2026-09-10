package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class ChargingPresentationTest {
    @Test fun boltsDistinguishChargingFastPausedAndFull() {
        assertEquals(0, ChargingPresentation.bolts(BatteryState(50)))
        assertEquals(1, ChargingPresentation.bolts(BatteryState(50, 1)))
        assertEquals(2, ChargingPresentation.bolts(BatteryState(50, 1, fast = true)))
        assertEquals(0, ChargingPresentation.bolts(BatteryState(50, 1, charging = false, fast = true)))
        assertEquals(0, ChargingPresentation.bolts(BatteryState(100, 1, full = true, fast = true)))
    }
    @Test fun fastRequiresChargingAndExplicitCapability() {
        assertFalse(ChargingPresentation.isFast(false, true, 3_000_000, 9_000_000))
        assertTrue(ChargingPresentation.isFast(true, true, 0, 0))
        assertTrue(ChargingPresentation.isFast(true, false, 3_000_000, 5_000_000))
        assertFalse(ChargingPresentation.isFast(true, false, 2_000_000, 5_000_000))
        assertFalse(ChargingPresentation.isFast(true, false, 3_000_000, 0))
        assertFalse(ChargingPresentation.isFast(true, false, -1, 9_000_000))
    }
    @Test fun estimateCountsDownAndRoundsUpMinutes() {
        val battery = BatteryState(80, 1, remainingMillis = 600_001, sampledAtElapsed = 1_000)
        assertEquals(11L, ChargingPresentation.remainingMinutes(battery, 1_000))
        assertEquals(10L, ChargingPresentation.remainingMinutes(battery, 1_001))
        assertEquals(9L, ChargingPresentation.remainingMinutes(battery, 61_001))
    }
    @Test fun elapsedEstimateNeverInventsCompletionOrNegativeTime() {
        val battery = BatteryState(80, 1, remainingMillis = 60_000, sampledAtElapsed = 1_000)
        assertNull(ChargingPresentation.remainingMinutes(battery, 61_000))
        assertNull(ChargingPresentation.remainingMinutes(battery, 70_000))
        assertNull(ChargingPresentation.remainingMinutes(battery, 999))
    }
    @Test fun unsupportedStalePausedAndFullEstimatesAreHidden() {
        val battery = BatteryState(80, 1, remainingMillis = 3_600_000, sampledAtElapsed = 1_000)
        assertNull(ChargingPresentation.remainingMinutes(battery, 601_001))
        assertNull(ChargingPresentation.remainingMinutes(battery.copy(remainingMillis = -1), 1_000))
        assertNull(ChargingPresentation.remainingMinutes(battery.copy(remainingMillis = 0), 1_000))
        assertNull(ChargingPresentation.remainingMinutes(battery.copy(charging = false), 1_000))
        assertNull(ChargingPresentation.remainingMinutes(battery.copy(full = true), 1_000))
    }
}
