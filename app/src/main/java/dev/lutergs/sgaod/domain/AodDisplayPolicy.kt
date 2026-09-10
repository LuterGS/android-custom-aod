package dev.lutergs.sgaod.domain

object AodBrightness {
    // Android's -1 sentinel delegates brightness to the system, including adaptive brightness.
    fun windowValue(visible: Boolean, automatic: Boolean, manualPercent: Int): Float = when {
        !visible -> 0f
        automatic -> -1f
        else -> manualPercent.coerceIn(1, 100) / 100f
    }
}

data class PixelOffset(val x: Int, val y: Int)

object BurnInProtection {
    fun offsetAt(timeMillis: Long): PixelOffset {
        val minute = Math.floorDiv(timeMillis, 60_000L)
        return PixelOffset(Math.floorMod(minute, 7L).toInt() - 3,
            Math.floorMod(Math.floorDiv(minute, 7L), 7L).toInt() - 3)
    }
    fun untilNextMinute(timeMillis: Long): Long = 60_000L - Math.floorMod(timeMillis, 60_000L)
}

enum class AodTouch { DOWN, MOVE, UP, CANCEL }

/** Consume hidden touches; a reveal must start a new stroke, never finish a pocket touch. */
class AodTouchGate {
    private var enabled = false
    private var stroke = false
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stroke = false
    }
    fun accept(action: AodTouch): Boolean {
        if (!enabled) return false
        if (action == AodTouch.DOWN) stroke = true
        val accepted = stroke
        if (action == AodTouch.UP || action == AodTouch.CANCEL) stroke = false
        return accepted
    }
}
