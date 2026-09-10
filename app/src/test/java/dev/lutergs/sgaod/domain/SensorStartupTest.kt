package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class SensorStartupTest {
    @Test fun waitsForBothRegisteredSensorsInEitherOrder() {
        val gate = SensorStartup()
        gate.reset(true, true)
        gate.proximityReceived()
        assertFalse(gate.ready)
        gate.orientationReceived()
        assertTrue(gate.ready)
        gate.reset(true, true)
        gate.orientationReceived()
        assertFalse(gate.ready)
        gate.proximityReceived()
        assertTrue(gate.ready)
    }
    @Test fun missingOrDisabledSensorsNeverImposeAFixedDelay() {
        val gate = SensorStartup()
        gate.reset(false, false)
        assertTrue(gate.ready)
        gate.reset(false, true)
        assertFalse(gate.ready)
        gate.orientationReceived()
        assertTrue(gate.ready)
        gate.reset(true, false)
        gate.proximityReceived()
        assertTrue(gate.ready)
    }
    @Test fun newSessionCannotReuseReadinessFromAnOldSession() {
        val gate = SensorStartup()
        gate.reset(true, true)
        gate.proximityReceived(); gate.orientationReceived()
        gate.reset(true, true)
        assertFalse(gate.ready)
        assertTrue(gate.proximityPending)
        assertTrue(gate.orientationPending)
    }
}
