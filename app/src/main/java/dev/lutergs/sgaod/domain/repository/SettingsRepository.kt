package dev.lutergs.sgaod.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 설정 데이터에 대한 Repository 인터페이스
 * DataStore 접근을 추상화하여 Domain Layer에서 Data Layer 의존성을 제거
 */
interface SettingsRepository {
    // AOD 활성화 상태
    val aodEnabled: Flow<Boolean>
    suspend fun setAodEnabled(enabled: Boolean)

    // 최대 알림 개수
    val maxNotificationCount: Flow<Int>
    suspend fun setMaxNotificationCount(count: Int)

    // 제외된 앱 목록
    val excludedApps: Flow<Set<String>>
    suspend fun addExcludedApp(packageName: String)
    suspend fun removeExcludedApp(packageName: String)
    suspend fun setExcludedApps(apps: Set<String>)

    // 상단 고정 앱 목록
    val pinnedApps: Flow<Set<String>>
    suspend fun addPinnedApp(packageName: String)
    suspend fun removePinnedApp(packageName: String)
    suspend fun setPinnedApps(apps: Set<String>)

    // 고정 알림 최대 개수
    val maxPinnedNotificationCount: Flow<Int>
    suspend fun setMaxPinnedNotificationCount(count: Int)

    // 24시간제 형식 (true: 24시간제, false: AM/PM)
    val timeFormat24h: Flow<Boolean>
    suspend fun setTimeFormat24h(use24h: Boolean)

    // 고정 알림 테두리 하이라이트
    val pinnedNotificationHighlight: Flow<Boolean>
    suspend fun setPinnedNotificationHighlight(enabled: Boolean)

    // 알림 시간 상대 표시 (true: "n분 전", false: "HH:mm")
    val notificationTimeRelative: Flow<Boolean>
    suspend fun setNotificationTimeRelative(useRelative: Boolean)

    // 하이라이트 테두리 색상 (HEX 형식)
    val highlightColor: Flow<String>
    suspend fun setHighlightColor(color: String)

    // AOD 글꼴 배율 (1.0 = 100%)
    val fontScale: Flow<Float>
    suspend fun setFontScale(scale: Float)
}
