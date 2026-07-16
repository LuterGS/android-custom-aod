package dev.lutergs.sgaod.domain.usecase.sensor

import dev.lutergs.sgaod.domain.repository.SensorRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 센서 모니터링을 관리하는 UseCase
 */
class ManageSensorMonitoringUseCase @Inject constructor(
    private val sensorRepository: SensorRepository
) {
    /**
     * 근접 센서 상태 (배터리 최적화에 사용)
     */
    val isProximityClose: StateFlow<Boolean> = sensorRepository.isProximityClose

    /**
     * 센서 모니터링 시작
     */
    fun startMonitoring() {
        sensorRepository.startMonitoring()
    }

    /**
     * 센서 모니터링 중지
     */
    fun stopMonitoring() {
        sensorRepository.stopMonitoring()
    }

    /**
     * 조도/가속도 센서 활성화/비활성화 (배터리 최적화)
     */
    fun setSecondarySensorsEnabled(enabled: Boolean) {
        sensorRepository.setSecondarySensorsEnabled(enabled)
    }
}
