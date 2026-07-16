package dev.lutergs.sgaod.domain.usecase.settings

import dev.lutergs.sgaod.domain.model.Settings
import dev.lutergs.sgaod.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 설정 관련 UseCase를 통합한 클래스
 * 모든 설정 읽기/쓰기를 하나의 클래스에서 관리하며, 도메인 검증 로직을 포함
 */
class SettingsUseCases @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    // Read flows - 직접 Repository에서 노출
    val aodEnabled: Flow<Boolean> = settingsRepository.aodEnabled
    val maxNotificationCount: Flow<Int> = settingsRepository.maxNotificationCount
    val excludedApps: Flow<Set<String>> = settingsRepository.excludedApps
    val pinnedApps: Flow<Set<String>> = settingsRepository.pinnedApps
    val maxPinnedNotificationCount: Flow<Int> = settingsRepository.maxPinnedNotificationCount
    val timeFormat24h: Flow<Boolean> = settingsRepository.timeFormat24h
    val pinnedNotificationHighlight: Flow<Boolean> = settingsRepository.pinnedNotificationHighlight
    val notificationTimeRelative: Flow<Boolean> = settingsRepository.notificationTimeRelative
    val highlightColor: Flow<String> = settingsRepository.highlightColor
    val fontScale: Flow<Float> = settingsRepository.fontScale

    // Write methods - 도메인 검증 포함
    suspend fun setAodEnabled(enabled: Boolean) {
        settingsRepository.setAodEnabled(enabled)
    }

    suspend fun setMaxNotificationCount(count: Int) {
        val validated = count.coerceIn(
            Settings.MIN_NOTIFICATION_COUNT,
            Settings.MAX_NOTIFICATION_COUNT
        )
        settingsRepository.setMaxNotificationCount(validated)
    }

    suspend fun setMaxPinnedNotificationCount(count: Int) {
        val validated = count.coerceIn(
            Settings.MIN_PINNED_NOTIFICATION_COUNT,
            Settings.MAX_PINNED_NOTIFICATION_COUNT
        )
        settingsRepository.setMaxPinnedNotificationCount(validated)
    }

    suspend fun addPinnedApp(packageName: String) {
        settingsRepository.addPinnedApp(packageName)
    }

    suspend fun removePinnedApp(packageName: String) {
        settingsRepository.removePinnedApp(packageName)
    }

    suspend fun setPinnedApps(apps: Set<String>) {
        val validated = apps.take(Settings.MAX_PINNED_APPS).toSet()
        settingsRepository.setPinnedApps(validated)
    }

    suspend fun setTimeFormat24h(use24h: Boolean) {
        settingsRepository.setTimeFormat24h(use24h)
    }

    suspend fun setPinnedNotificationHighlight(enabled: Boolean) {
        settingsRepository.setPinnedNotificationHighlight(enabled)
    }

    suspend fun setNotificationTimeRelative(useRelative: Boolean) {
        settingsRepository.setNotificationTimeRelative(useRelative)
    }

    suspend fun setHighlightColor(color: String) {
        // 도메인 검증: 6자리 HEX 만 저장 — 잘못된 값이 저장되면 UI 파싱 시 크래시 위험
        val normalized = color.removePrefix("#").uppercase()
        val validated = if (normalized.length == 6 && normalized.all { it.isDigit() || it in 'A'..'F' }) {
            normalized
        } else {
            Settings.DEFAULT_HIGHLIGHT_COLOR
        }
        settingsRepository.setHighlightColor(validated)
    }

    suspend fun setFontScale(scale: Float) {
        val validated = scale.coerceIn(Settings.MIN_FONT_SCALE, Settings.MAX_FONT_SCALE)
        settingsRepository.setFontScale(validated)
    }

    suspend fun addExcludedApp(packageName: String) {
        settingsRepository.addExcludedApp(packageName)
    }

    suspend fun removeExcludedApp(packageName: String) {
        settingsRepository.removeExcludedApp(packageName)
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        settingsRepository.setExcludedApps(apps)
    }
}
