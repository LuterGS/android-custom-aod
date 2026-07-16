package dev.lutergs.sgaod.domain.model

/**
 * 배터리 정보를 담는 도메인 모델
 */
data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean
) {
    val percentage: String get() = "$level%"
}
