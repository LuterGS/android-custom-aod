package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class FrameGateTest {
    @Test fun initialFrameDoesNotWait() { assertEquals(0L, FrameGate().delay(0)) }
    @Test fun burstWaitsForOneSecondBoundary() {
        val gate = FrameGate()
        gate.rendered(5_000)
        assertEquals(1_000L, gate.delay(5_000))
        assertEquals(990L, gate.delay(5_010))
        assertEquals(1L, gate.delay(5_999))
        assertEquals(0L, gate.delay(6_000))
    }
    @Test fun quietPeriodNeedsNoCatchUpFrames() {
        val gate = FrameGate()
        gate.rendered(1_000)
        assertEquals(0L, gate.delay(1_000_000))
        gate.rendered(1_000_000)
        assertEquals(1_000L, gate.delay(1_000_000))
    }
    @Test fun eventFloodNeverExceedsOneFramePerSecond() {
        val gate = FrameGate()
        val frames = mutableListOf<Long>()
        for (now in 0L..10_000L step 7) {
            if (gate.delay(now) == 0L) { gate.rendered(now); frames += now }
        }
        assertTrue(frames.size <= 11)
        assertTrue(frames.zipWithNext().all { (a, b) -> b - a >= 1_000 })
    }
    @Test fun initialBlackBufferDoesNotDelayFirstContent() {
        val gate = FrameGate()
        gate.rendered(100, content = false)
        assertEquals(0L, gate.delay(101))
    }
    @Test fun blackoutDoesNotResetContentThrottle() {
        val gate = FrameGate()
        gate.rendered(0)
        gate.rendered(900, content = false)
        assertEquals(100L, gate.delay(900))
        assertEquals(0L, gate.delay(1_000))
    }
    @Test fun restoreStillCannotSubmitContentFasterThanOneFps() {
        val gate = FrameGate()
        gate.rendered(1_000)
        gate.rendered(1_050, content = false)
        assertEquals(750L, gate.delay(1_250))
        gate.rendered(2_000)
        assertEquals(1_000L, gate.delay(2_000))
    }
}
