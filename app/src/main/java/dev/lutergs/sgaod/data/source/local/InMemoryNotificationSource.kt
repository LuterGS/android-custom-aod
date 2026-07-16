package dev.lutergs.sgaod.data.source.local

import dev.lutergs.sgaod.domain.model.NotificationGroup
import dev.lutergs.sgaod.domain.model.NotificationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인메모리 알림 저장소
 * 앱 실행 중에만 유지되는 알림 데이터를 관리
 *
 * PendingIntent, Icon은 각각 PendingIntentStore, NotificationIconStore에서 관리
 *
 * 참고: 이전의 _pinnedAppCache(지워진 고정 앱 알림 부활 캐시)는 제거됨 —
 * 원래 의미론에서도 사용자가 지운 알림은 캐시에서 삭제됐으므로, 캐시의 유일한
 * 관측 가능 효과는 '리스너가 놓친 삭제 이벤트의 알림을 좀비로 되살리는 것'뿐이었다.
 */
@Singleton
class InMemoryNotificationSource @Inject constructor() {

    @Volatile
    private var _maxNotifications = 4

    @Volatile
    private var _maxPinnedNotifications = 3

    @Volatile
    private var _pinnedApps: Set<String> = emptySet()

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

    private val _groupedPinnedNotifications = MutableStateFlow<List<NotificationGroup>>(emptyList())
    val groupedPinnedNotifications: StateFlow<List<NotificationGroup>> = _groupedPinnedNotifications.asStateFlow()

    private val _groupedRegularNotifications = MutableStateFlow<List<NotificationGroup>>(emptyList())
    val groupedRegularNotifications: StateFlow<List<NotificationGroup>> = _groupedRegularNotifications.asStateFlow()

    private val _recentApps = MutableStateFlow<Set<String>>(emptySet())
    val recentApps: StateFlow<Set<String>> = _recentApps.asStateFlow()

    @Synchronized
    fun setPinnedApps(apps: Set<String>) {
        _pinnedApps = apps
        refreshNotificationLists()
    }

    @Synchronized
    fun setMaxNotifications(count: Int) {
        _maxNotifications = count
        refreshNotificationLists()
    }

    @Synchronized
    fun setMaxPinnedNotifications(count: Int) {
        _maxPinnedNotifications = count
        refreshNotificationLists()
    }

    private fun refreshNotificationLists() {
        val allNotifications = _notifications.value

        val (pinned, regular) = allNotifications.partition { _pinnedApps.contains(it.packageName) }

        _groupedPinnedNotifications.value = groupByConversation(pinned)
            .take(_maxPinnedNotifications)

        _groupedRegularNotifications.value = groupByConversation(regular)
            .take(_maxNotifications)
    }

    /**
     * 알림을 대화(채팅방/채널) 단위로 그룹화하여 각 그룹의 최신 알림 1개와 전체 개수를 반환.
     * 메시징 앱은 방별로 분리되고(conversationKey), 그 외 앱은 앱 단위로 묶인다.
     */
    private fun groupByConversation(notifications: List<NotificationInfo>): List<NotificationGroup> {
        return notifications
            .groupBy { it.conversationKey }
            .map { (_, notifs) ->
                val latest = notifs.maxBy { it.postTime }
                NotificationGroup(
                    packageName = latest.packageName,
                    appName = latest.appName,
                    latestNotification = latest,
                    totalCount = notifs.size
                )
            }
            .sortedByDescending { it.latestNotification.postTime }
    }

    /**
     * 전체 알림 재동기화 (리스너 연결/재연결, AOD 표시 시점 등).
     * activeNotifications 를 유일한 진실 소스로 삼아 놓친 이벤트를 복구한다.
     */
    @Synchronized
    fun updateNotifications(notificationList: List<NotificationInfo>) {
        _recentApps.value = _recentApps.value + notificationList.map { it.packageName }
        _notifications.value = notificationList
        refreshNotificationLists()
    }

    @Synchronized
    fun addNotification(notification: NotificationInfo) {
        val currentList = _notifications.value.toMutableList()
        currentList.removeAll { it.key == notification.key }
        currentList.add(notification)

        _notifications.value = currentList
        refreshNotificationLists()
    }

    @Synchronized
    fun removeNotification(key: String) {
        val currentList = _notifications.value.toMutableList()
        val removed = currentList.removeAll { it.key == key }
        if (!removed) return

        _notifications.value = currentList
        refreshNotificationLists()
    }

    @Synchronized
    fun clearAll() {
        _notifications.value = emptyList()
        _groupedPinnedNotifications.value = emptyList()
        _groupedRegularNotifications.value = emptyList()
    }
}
