package dev.lutergs.sgaod.data

import android.content.Context
import android.hardware.*
import android.os.Handler
import dev.lutergs.sgaod.domain.AodPolicy
import dev.lutergs.sgaod.domain.SensorStartup
import dev.lutergs.sgaod.domain.SensorTiming

/** Only registered during a locked AOD session. Wake-up sensors are preferred, never emulated with CPU locks. */
class EnvironmentSensors(context: Context, private val handler: Handler,
    private val changed: (Boolean, Boolean) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
        ?: manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY, true)
        ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var near = false
    private var down = false
    private var proposedDown = false
    private var running = false
    private val startup = SensorStartup()
    val hasInitialState: Boolean get() = startup.ready
    private val confirmDown = Runnable { down = proposedDown; changed(near, down) }
    private val confirmFar = Runnable { near = false; changed(near, down) }

    fun start(pocket: Boolean, faceDown: Boolean) {
        stop()
        running = true
        val waitProximity = pocket && proximity?.let { manager.registerListener(this, it, 200_000, handler) } == true
        // 2 Hz requested; vendors can deliver faster. Stable orientation changes are debounced below.
        val waitGravity = faceDown && gravity?.let { manager.registerListener(this, it, 500_000, handler) } == true
        startup.reset(waitProximity, waitGravity)
        if (startup.ready) changed(near, down)
    }
    fun stop() {
        manager.unregisterListener(this)
        handler.removeCallbacks(confirmDown)
        handler.removeCallbacks(confirmFar)
        running = false
        near = false
        down = false
        proposedDown = false
        startup.reset(false, false)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            if (!event.values[0].isFinite() || event.values[0] < 0f) return
            val initial = startup.proximityPending
            startup.proximityReceived()
            val value = AodPolicy.covered(event.values[0], event.sensor.maximumRange)
            if (value) {
                handler.removeCallbacks(confirmFar)
                if (!near) { near = true; changed(near, down); return }
            } else if (near && !handler.hasCallbacks(confirmFar)) {
                handler.postDelayed(confirmFar, SensorTiming.UNCOVER_DELAY_MS)
            }
            if (initial) changed(near, down)
        } else {
            if (!event.values[2].isFinite()) return
            val initial = startup.orientationPending
            startup.orientationReceived()
            val value = AodPolicy.faceDown(event.values[2], proposedDown)
            if (initial) {
                // A phone already face down must not flash on while entry debounce runs.
                down = value
                proposedDown = value
                changed(near, down)
                return
            }
            if (value != proposedDown) {
                proposedDown = value
                handler.removeCallbacks(confirmDown)
                if (value != down) handler.postDelayed(confirmDown,
                    if (value) SensorTiming.FACE_DOWN_DELAY_MS else SensorTiming.LIFT_DELAY_MS)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
