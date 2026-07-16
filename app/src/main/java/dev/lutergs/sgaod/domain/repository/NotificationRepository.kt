package dev.lutergs.sgaod.domain.repository

import dev.lutergs.sgaod.domain.model.NotificationGroup
import dev.lutergs.sgaod.domain.model.NotificationInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 알림 데이터에 대한 Repository 인터페이스
 * 인메모리 알림 저장소를 추상화
 *
 * PendingIntent는 Android Framework 타입이므로 Domain Layer에서 제외
 * Data Layer의 PendingIntentStore에서 직접 관리
 */
interface NotificationRepository {
    // 현재 알림 목록 (최대 개수 및 우선순위 적용)
    val notifications: StateFlow<List<NotificationInfo>>

    // 최근 알림이 온 앱 목록 (고정 앱 선택 UI용)
    val recentApps: StateFlow<Set<String>>

    // 앱별로 그룹화된 고정 알림 목록
    val groupedPinnedNotifications: StateFlow<List<NotificationGroup>>

    // 앱별로 그룹화된 일반 알림 목록
    val groupedRegularNotifications: StateFlow<List<NotificationGroup>>

    // 고정 앱 목록 설정
    fun setPinnedApps(apps: Set<String>)

    // 최대 알림 개수 설정
    fun setMaxNotifications(count: Int)

    // 고정 알림 최대 개수 설정
    fun setMaxPinnedNotifications(count: Int)

    // 알림 목록 전체 업데이트
    fun updateNotifications(notificationList: List<NotificationInfo>)

    // 새 알림 추가
    fun addNotification(notification: NotificationInfo)

    // 알림 제거
    fun removeNotification(key: String)

    // 모든 알림 제거
    fun clearAll()
}
