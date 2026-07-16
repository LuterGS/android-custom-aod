package dev.lutergs.sgaod.data.source.local

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.lutergs.sgaod.domain.model.SensorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs

/**
 * 센서 데이터를 제공하는 DataSource
 * 근접 센서: 항상 활성화 (Sleep 모드 진입/해제 감지)
 * 조도/가속도 센서: 근접 해제 시에만 활성화 (배터리 최적화)
 */
class SensorDataSource @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val MOTION_THRESHOLD = 0.5f
        private const val STILLNESS_DURATION_NS = 10_000_000_000L  // 10초
        // 조도 센서 스로틀링 상수
        private const val LUX_CHANGE_THRESHOLD = 5f  // 5 lux 이상 변화시에만 업데이트
        private const val LUX_UPDATE_MIN_INTERVAL_NS = 2_000_000_000L  // 최소 2초 간격

        // 하드웨어 FIFO 배칭 지연 — AP 를 깨우는 빈도를 줄여 배터리 절약
        private const val ACCEL_MAX_REPORT_LATENCY_US = 2_000_000  // 2초
        private const val LIGHT_MAX_REPORT_LATENCY_US = 5_000_000  // 5초
    }

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val _sensorState = MutableStateFlow(SensorState.initial())
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _isProximityClose = MutableStateFlow(false)
    val isProximityClose: StateFlow<Boolean> = _isProximityClose.asStateFlow()

    // 가속도 센서 상태 (시간 기준: SensorEvent.timestamp — 단조 증가 나노초)
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var isFirstAccelReading = true
    private var lastMotionTimestampNs = 0L

    // 조도 센서 스로틀링 상태
    private var lastReportedLux = 0f
    private var lastLuxUpdateTimestampNs = 0L

    // 가속도 센서 스로틀링 상태
    private var lastReportedStillState = false

    // 센서 리스너들
    private var proximityListener: SensorEventListener? = null
    private var secondarySensorListener: SensorEventListener? = null

    // 근접 센서 최대 범위
    private val proximityMaxRange: Float by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: 5f
    }

    /**
     * 근접 센서 모니터링 시작 (항상 활성화)
     */
    fun startProximityMonitoring() {
        if (proximityListener != null) return

        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                    val isClose = event.values[0] < proximityMaxRange
                    _isProximityClose.value = isClose
                    _sensorState.update { it.copy(isProximityClose = isClose) }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        proximitySensor?.let {
            sensorManager.registerListener(
                proximityListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    /**
     * 근접 센서 모니터링 중지
     */
    fun stopProximityMonitoring() {
        proximityListener?.let {
            sensorManager.unregisterListener(it)
            proximityListener = null
        }
    }

    /**
     * 조도/가속도 센서 모니터링 시작 (배터리 최적화)
     */
    fun startSecondarySensorsMonitoring() {
        if (secondarySensorListener != null) return

        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 가속도 상태 초기화
        isFirstAccelReading = true
        lastMotionTimestampNs = 0L

        secondarySensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        handleLightSensorEvent(event)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        handleAccelerometerEvent(event)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // 4-인자 오버로드로 하드웨어 FIFO 배칭을 활성화해 AP 웨이크업 빈도를 줄인다
        lightSensor?.let {
            sensorManager.registerListener(
                secondarySensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                LIGHT_MAX_REPORT_LATENCY_US
            )
        }
        accelerometer?.let {
            sensorManager.registerListener(
                secondarySensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                ACCEL_MAX_REPORT_LATENCY_US
            )
        }
    }

    /**
     * 조도/가속도 센서 모니터링 중지 (배터리 절약)
     */
    fun stopSecondarySensorsMonitoring() {
        secondarySensorListener?.let {
            sensorManager.unregisterListener(it)
            secondarySensorListener = null
        }
    }

    /**
     * 모든 센서 모니터링 중지
     */
    fun stopAllMonitoring() {
        stopProximityMonitoring()
        stopSecondarySensorsMonitoring()
    }

    /**
     * 조도 센서 이벤트 처리 (스로틀링 적용)
     * - 조도 변화가 임계값 이상일 때만 업데이트
     * - 최소 업데이트 간격 유지
     */
    private fun handleLightSensorEvent(event: SensorEvent) {
        val newLux = event.values[0]
        val luxDiff = abs(newLux - lastReportedLux)

        // 조도 변화가 임계값 이상이고, 최소 간격이 지났을 때만 업데이트
        if (luxDiff >= LUX_CHANGE_THRESHOLD &&
            (event.timestamp - lastLuxUpdateTimestampNs) >= LUX_UPDATE_MIN_INTERVAL_NS) {
            lastReportedLux = newLux
            lastLuxUpdateTimestampNs = event.timestamp
            _sensorState.update { it.copy(lux = newLux) }
        }
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (isFirstAccelReading) {
            lastAccelX = x
            lastAccelY = y
            lastAccelZ = z
            isFirstAccelReading = false
            lastMotionTimestampNs = event.timestamp
            return
        }

        val deltaX = abs(x - lastAccelX)
        val deltaY = abs(y - lastAccelY)
        val deltaZ = abs(z - lastAccelZ)
        val totalDelta = deltaX + deltaY + deltaZ

        lastAccelX = x
        lastAccelY = y
        lastAccelZ = z

        // 벽시계(System.currentTimeMillis) 대신 단조 증가 timestamp 사용
        // — 사용자의 시간 변경/NTP 동기화에 영향받지 않음
        val isStill = if (totalDelta > MOTION_THRESHOLD) {
            lastMotionTimestampNs = event.timestamp
            false
        } else {
            (event.timestamp - lastMotionTimestampNs) > STILLNESS_DURATION_NS
        }

        // 상태가 변경될 때만 StateFlow 업데이트 (불필요한 리컴포지션 방지)
        if (isStill != lastReportedStillState) {
            lastReportedStillState = isStill
            _sensorState.update { it.copy(isDeviceStill = isStill) }
        }
    }
}
