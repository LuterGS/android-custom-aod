package dev.lutergs.sgaod.data

import android.content.Context
import android.hardware.*
import android.os.Handler
import dev.lutergs.sgaod.domain.AodPolicy

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
    private val confirmDown = Runnable { down = proposedDown; changed(near, down) }
    private val confirmFar = Runnable { near = false; changed(near, down) }

    fun start(pocket: Boolean, faceDown: Boolean) {
        stop()
        running = true
        if (pocket) proximity?.let { manager.registerListener(this, it, 200_000, handler) }
        // 2 Hz requested; vendors can deliver faster. Stable orientation changes are debounced below.
        if (faceDown) gravity?.let { manager.registerListener(this, it, 500_000, handler) }
    }
    fun stop() {
        manager.unregisterListener(this)
        handler.removeCallbacks(confirmDown)
        handler.removeCallbacks(confirmFar)
        running = false
        near = false
        down = false
        proposedDown = false
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val value = AodPolicy.covered(event.values[0], event.sensor.maximumRange)
            if (value) {
                handler.removeCallbacks(confirmFar)
                if (!near) { near = true; changed(near, down) }
            } else if (near && !handler.hasCallbacks(confirmFar)) {
                handler.postDelayed(confirmFar, 1_200)
            }
        } else {
            val value = AodPolicy.faceDown(event.values[2], proposedDown)
            if (value != proposedDown) {
                proposedDown = value
                handler.removeCallbacks(confirmDown)
                if (value != down) handler.postDelayed(confirmDown, if (value) 700 else 1_200)
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
