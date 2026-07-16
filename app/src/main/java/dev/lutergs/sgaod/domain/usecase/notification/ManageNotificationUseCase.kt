package dev.lutergs.sgaod.domain.usecase.notification

import dev.lutergs.sgaod.domain.model.NotificationInfo
import dev.lutergs.sgaod.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * 알림을 추가/제거/업데이트하는 UseCase
 *
 * PendingIntent는 Android Framework 타입이므로 Domain Layer에서 제외
 * Data Layer의 PendingIntentStore에서 직접 관리
 */
class ManageNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    fun add(notification: NotificationInfo) {
        notificationRepository.addNotification(notification)
    }

    fun remove(key: String) {
        notificationRepository.removeNotification(key)
    }

    fun updateAll(notifications: List<NotificationInfo>) {
        notificationRepository.updateNotifications(notifications)
    }

    fun clearAll() {
        notificationRepository.clearAll()
    }
}
