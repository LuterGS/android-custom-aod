package dev.lutergs.sgaod.domain.model

/**
 * 앱별로 그룹화된 알림을 나타내는 도메인 모델
 *
 * 같은 앱에서 온 여러 알림을 하나의 그룹으로 묶어
 * 최신 알림 1개만 표시하고, 전체 개수를 badge로 표시하기 위해 사용
 */
data class NotificationGroup(
    val packageName: String,
    val appName: String,
    val latestNotification: NotificationInfo,
    val totalCount: Int
) {
    /**
     * 해당 앱에서 표시되지 않은 추가 알림이 있는지 여부
     * totalCount > 1이면 badge 표시가 필요함
     */
    val hasAdditionalNotifications: Boolean get() = totalCount > 1
}
