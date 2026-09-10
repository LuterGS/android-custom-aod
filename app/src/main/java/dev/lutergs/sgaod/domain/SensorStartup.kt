package dev.lutergs.sgaod.domain

/** Only sensors successfully registered for this session can hold up the first frame. */
class SensorStartup {
    var proximityPending = false
        private set
    var orientationPending = false
        private set
    val ready: Boolean get() = !proximityPending && !orientationPending
    fun reset(proximity: Boolean, orientation: Boolean) {
        proximityPending = proximity
        orientationPending = orientation
    }
    fun proximityReceived() { proximityPending = false }
    fun orientationReceived() { orientationPending = false }
}

object SensorTiming {
    const val STARTUP_TIMEOUT_MS = 1_000L
    const val UNCOVER_DELAY_MS = 200L
    const val FACE_DOWN_DELAY_MS = 700L
    const val LIFT_DELAY_MS = 200L
}
