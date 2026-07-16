package dev.lutergs.sgaod.data.repository

import dev.lutergs.sgaod.data.source.local.SensorDataSource
import dev.lutergs.sgaod.domain.model.SensorState
import dev.lutergs.sgaod.domain.repository.SensorRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * SensorRepository 구현체
 */
class SensorRepositoryImpl @Inject constructor(
    private val sensorDataSource: SensorDataSource
) : SensorRepository {

    override val sensorState: StateFlow<SensorState> = sensorDataSource.sensorState

    override val isProximityClose: StateFlow<Boolean> = sensorDataSource.isProximityClose

    override fun startMonitoring() {
        // 근접 센서는 항상 활성화
        sensorDataSource.startProximityMonitoring()
        // 조도/가속도 센서도 시작 (초기 상태)
        sensorDataSource.startSecondarySensorsMonitoring()
    }

    override fun stopMonitoring() {
        sensorDataSource.stopAllMonitoring()
    }

    override fun setSecondarySensorsEnabled(enabled: Boolean) {
        if (enabled) {
            sensorDataSource.startSecondarySensorsMonitoring()
        } else {
            sensorDataSource.stopSecondarySensorsMonitoring()
        }
    }
}
