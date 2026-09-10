package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class AodDisplayPolicyTest {
    @Test fun automaticUsesSystemRegardlessOfSavedManualBrightness() {
        for (manual in listOf(1, 3, 70, 100)) assertEquals(-1f, AodBrightness.windowValue(true, true, manual), 0f)
    }
    @Test fun blackoutOverridesAutomaticAndManualBrightness() {
        for (automatic in listOf(true, false)) assertEquals(0f, AodBrightness.windowValue(false, automatic, 100), 0f)
    }
    @Test fun disablingAutomaticRestoresSavedManualValue() {
        assertEquals(-1f, AodBrightness.windowValue(true, true, 17), 0f)
        assertEquals(0.17f, AodBrightness.windowValue(true, false, 17), 0.001f)
        assertEquals(0.01f, AodBrightness.windowValue(true, false, 0), 0f)
        assertEquals(1f, AodBrightness.windowValue(true, false, 120), 0f)
    }
    @Test fun hiddenTouchesAreConsumedAndCannotBecomeAStrokeOnReveal() {
        val gate = AodTouchGate()
        assertFalse(gate.accept(AodTouch.DOWN))
        gate.setEnabled(true)
        assertFalse(gate.accept(AodTouch.MOVE))
        assertFalse(gate.accept(AodTouch.UP))
        assertTrue(gate.accept(AodTouch.DOWN))
        assertTrue(gate.accept(AodTouch.UP))
    }
    @Test fun coveringDuringATouchCancelsItsRemainder() {
        val gate = AodTouchGate()
        gate.setEnabled(true)
        assertTrue(gate.accept(AodTouch.DOWN))
        gate.setEnabled(false)
        assertFalse(gate.accept(AodTouch.MOVE))
        gate.setEnabled(true)
        assertFalse(gate.accept(AodTouch.UP))
        assertTrue(gate.accept(AodTouch.DOWN))
        assertTrue(gate.accept(AodTouch.CANCEL))
        assertFalse(gate.accept(AodTouch.UP))
    }
    @Test fun burnInShiftChangesEveryMinuteButNeverWithinOneMinute() {
        for (minute in 0L until 49L) {
            val offset = BurnInProtection.offsetAt(minute * 60_000)
            assertEquals(offset, BurnInProtection.offsetAt(minute * 60_000 + 59_999))
            assertNotEquals(offset, BurnInProtection.offsetAt((minute + 1) * 60_000))
        }
    }
    @Test fun burnInOrbitStaysWithinThreePhysicalPixelsAndVisits49Positions() {
        val positions = (0L until 49L).map { BurnInProtection.offsetAt(it * 60_000) }.toSet()
        assertEquals(49, positions.size)
        assertTrue(positions.all { it.x in -3..3 && it.y in -3..3 })
        assertEquals(BurnInProtection.offsetAt(0), BurnInProtection.offsetAt(49 * 60_000))
    }
    @Test fun minuteSchedulingNeverAddsAZeroDelayLoop() {
        assertEquals(60_000L, BurnInProtection.untilNextMinute(0))
        assertEquals(1L, BurnInProtection.untilNextMinute(59_999))
        assertEquals(60_000L, BurnInProtection.untilNextMinute(60_000))
        assertEquals(1L, BurnInProtection.untilNextMinute(-1))
    }
}
