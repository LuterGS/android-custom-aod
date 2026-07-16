package dev.lutergs.sgaod.domain.usecase.notification

import dev.lutergs.sgaod.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * 알림 설정(고정 앱, 최대 개수)을 NotificationRepository에 반영하는 UseCase
 */
class UpdateNotificationSettingsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    fun setPinnedApps(apps: Set<String>) {
        notificationRepository.setPinnedApps(apps)
    }

    fun setMaxNotifications(count: Int) {
        notificationRepository.setMaxNotifications(count)
    }

    fun setMaxPinnedNotifications(count: Int) {
        notificationRepository.setMaxPinnedNotifications(count)
    }
}
