package dev.lutergs.sgaod.domain.model

/**
 * 센서 상태를 나타내는 도메인 모델
 */
data class SensorState(
    val lux: Float = 100f,
    val isDeviceStill: Boolean = false,
    val isProximityClose: Boolean = false
) {
    companion object {
        private const val SLEEP_LUX_THRESHOLD = 5f

        fun initial() = SensorState()
    }

    /**
     * Sleep 모드 여부 (조도 낮음 + 정지 상태 OR 근접 센서 가까움)
     */
    val isSleepMode: Boolean
        get() = (lux < SLEEP_LUX_THRESHOLD && isDeviceStill) || isProximityClose
}
