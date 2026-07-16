package dev.lutergs.sgaod.data.repository

import dev.lutergs.sgaod.data.source.local.InMemoryNotificationSource
import dev.lutergs.sgaod.domain.model.NotificationGroup
import dev.lutergs.sgaod.domain.model.NotificationInfo
import dev.lutergs.sgaod.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationRepository 구현체
 * InMemoryNotificationSource를 사용하여 알림 데이터를 관리
 *
 * PendingIntent, Icon은 각각 PendingIntentStore, NotificationIconStore에서 관리
 */
@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationSource: InMemoryNotificationSource
) : NotificationRepository {

    override val notifications: StateFlow<List<NotificationInfo>> = notificationSource.notifications

    override val recentApps: StateFlow<Set<String>> = notificationSource.recentApps

    override val groupedPinnedNotifications: StateFlow<List<NotificationGroup>> = notificationSource.groupedPinnedNotifications

    override val groupedRegularNotifications: StateFlow<List<NotificationGroup>> = notificationSource.groupedRegularNotifications

    override fun setPinnedApps(apps: Set<String>) {
        notificationSource.setPinnedApps(apps)
    }

    override fun setMaxNotifications(count: Int) {
        notificationSource.setMaxNotifications(count)
    }

    override fun setMaxPinnedNotifications(count: Int) {
        notificationSource.setMaxPinnedNotifications(count)
    }

    override fun updateNotifications(notificationList: List<NotificationInfo>) {
        notificationSource.updateNotifications(notificationList)
    }

    override fun addNotification(notification: NotificationInfo) {
        notificationSource.addNotification(notification)
    }

    override fun removeNotification(key: String) {
        notificationSource.removeNotification(key)
    }

    override fun clearAll() {
        notificationSource.clearAll()
    }
}
