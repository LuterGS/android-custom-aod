package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class AodPolicyTest {
    private val settings = AodSettings(enabled = true)
    @Test fun visibleOnDesk() { assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, Environment())) }
    @Test fun pocketBlacksOutEvenWhileCharging() {
        assertEquals(SleepReason.COVERED, AodPolicy.sleepReason(settings, Environment(covered = true, charging = true)))
    }
    @Test fun faceDownBlacksOut() {
        assertEquals(SleepReason.FACE_DOWN, AodPolicy.sleepReason(settings, Environment(faceDown = true)))
    }
    @Test fun uncoveringDoesNotOverrideLowBattery() {
        assertEquals(SleepReason.LOW_BATTERY, AodPolicy.sleepReason(settings, Environment(covered = false, batteryPercent = 15)))
    }
    @Test fun chargingExemptsLowBattery() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, Environment(batteryPercent = 1, charging = true)))
    }
    @Test fun lowBatteryBoundary() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, Environment(batteryPercent = 16)))
        assertEquals(SleepReason.LOW_BATTERY, AodPolicy.sleepReason(settings, Environment(batteryPercent = 0)))
    }
    @Test fun unavailableBatteryReadingIsNotZeroPercent() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, Environment(batteryPercent = -1)))
    }
    @Test fun powerSavingIsRespected() {
        assertEquals(SleepReason.POWER_SAVER, AodPolicy.sleepReason(settings, Environment(powerSaver = true)))
    }
    @Test fun powerSavingPreferenceCanBeDisabled() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings.copy(respectPowerSaver = false), Environment(powerSaver = true)))
    }
    @Test fun chargingDoesNotOverrideThermalProtection() {
        assertEquals(SleepReason.HOT, AodPolicy.sleepReason(settings, Environment(hot = true, charging = true)))
    }
    @Test fun scheduleCrossesMidnight() {
        val night = settings.copy(sleepAtNight = true)
        for (hour in listOf(23, 0, 6)) assertEquals(SleepReason.NIGHT, AodPolicy.sleepReason(night, Environment(hour = hour)))
        for (hour in listOf(7, 12, 22)) assertEquals(SleepReason.NONE, AodPolicy.sleepReason(night, Environment(hour = hour)))
    }
    @Test fun scheduleIsOptional() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, Environment(hour = 0)))
    }
    @Test fun idleTimeoutBlacksOut() { assertEquals(SleepReason.IDLE, AodPolicy.sleepReason(settings, Environment(idle = true))) }
    @Test fun callsPreventDisplay() { assertEquals(SleepReason.CALL, AodPolicy.sleepReason(settings, Environment(inCall = true))) }
    @Test fun disabledSensorsDoNotBlockDisplay() {
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings.copy(pocketDetection = false, faceDownDetection = false),
            Environment(covered = true, faceDown = true)))
    }
    @Test fun faceDownThresholdHasHysteresis() {
        assertFalse(AodPolicy.faceDown(-7f, false))
        assertTrue(AodPolicy.faceDown(-9f, false))
        assertTrue(AodPolicy.faceDown(-7f, true))
        assertFalse(AodPolicy.faceDown(-5f, true))
    }
    @Test fun proximityWorksForBinaryAndCentimeterSensors() {
        assertTrue(AodPolicy.covered(0f, 1f))
        assertFalse(AodPolicy.covered(1f, 1f))
        assertTrue(AodPolicy.covered(3f, 10f))
        assertFalse(AodPolicy.covered(5f, 10f))
        assertFalse(AodPolicy.covered(Float.NaN, 5f))
        assertFalse(AodPolicy.covered(-1f, 5f))
    }
    @Test fun changingSleepReasonCannotRestoreUntilAllBlockersClear() {
        var environment = Environment(covered = true, faceDown = true, powerSaver = true)
        assertEquals(SleepReason.COVERED, AodPolicy.sleepReason(settings, environment))
        environment = environment.copy(covered = false)
        assertEquals(SleepReason.FACE_DOWN, AodPolicy.sleepReason(settings, environment))
        environment = environment.copy(faceDown = false)
        assertEquals(SleepReason.POWER_SAVER, AodPolicy.sleepReason(settings, environment))
        environment = environment.copy(powerSaver = false)
        assertEquals(SleepReason.NONE, AodPolicy.sleepReason(settings, environment))
    }
}
