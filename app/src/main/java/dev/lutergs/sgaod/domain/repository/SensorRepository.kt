package dev.lutergs.sgaod.domain.repository

import dev.lutergs.sgaod.domain.model.SensorState
import kotlinx.coroutines.flow.StateFlow

/**
 * 센서 데이터를 제공하는 Repository 인터페이스
 */
interface SensorRepository {
    /**
     * 현재 센서 상태를 Flow로 관찰
     */
    val sensorState: StateFlow<SensorState>

    /**
     * 근접 센서 상태만 관찰 (항상 활성화)
     */
    val isProximityClose: StateFlow<Boolean>

    /**
     * 센서 모니터링 시작
     */
    fun startMonitoring()

    /**
     * 센서 모니터링 중지
     */
    fun stopMonitoring()

    /**
     * 근접 상태에 따라 조도/가속도 센서 제어 (배터리 최적화)
     */
    fun setSecondarySensorsEnabled(enabled: Boolean)
}
