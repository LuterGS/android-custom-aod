package dev.lutergs.sgaod.data.repository

import dev.lutergs.sgaod.data.source.local.DataStoreSource
import dev.lutergs.sgaod.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SettingsRepository 구현체
 * DataStoreSource를 사용하여 설정 데이터를 관리
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStoreSource: DataStoreSource
) : SettingsRepository {

    override val aodEnabled: Flow<Boolean> = dataStoreSource.aodEnabled

    override suspend fun setAodEnabled(enabled: Boolean) {
        dataStoreSource.setAodEnabled(enabled)
    }

    override val maxNotificationCount: Flow<Int> = dataStoreSource.maxNotificationCount

    override suspend fun setMaxNotificationCount(count: Int) {
        dataStoreSource.setMaxNotificationCount(count)
    }

    override val excludedApps: Flow<Set<String>> = dataStoreSource.excludedApps

    override suspend fun addExcludedApp(packageName: String) {
        dataStoreSource.addExcludedApp(packageName)
    }

    override suspend fun removeExcludedApp(packageName: String) {
        dataStoreSource.removeExcludedApp(packageName)
    }

    override suspend fun setExcludedApps(apps: Set<String>) {
        dataStoreSource.setExcludedApps(apps)
    }

    override val pinnedApps: Flow<Set<String>> = dataStoreSource.pinnedApps

    override suspend fun addPinnedApp(packageName: String) {
        dataStoreSource.addPinnedApp(packageName)
    }

    override suspend fun removePinnedApp(packageName: String) {
        dataStoreSource.removePinnedApp(packageName)
    }

    override suspend fun setPinnedApps(apps: Set<String>) {
        dataStoreSource.setPinnedApps(apps)
    }

    override val maxPinnedNotificationCount: Flow<Int> = dataStoreSource.maxPinnedNotificationCount

    override suspend fun setMaxPinnedNotificationCount(count: Int) {
        dataStoreSource.setMaxPinnedNotificationCount(count)
    }

    override val timeFormat24h: Flow<Boolean> = dataStoreSource.timeFormat24h

    override suspend fun setTimeFormat24h(use24h: Boolean) {
        dataStoreSource.setTimeFormat24h(use24h)
    }

    override val pinnedNotificationHighlight: Flow<Boolean> = dataStoreSource.pinnedNotificationHighlight

    override suspend fun setPinnedNotificationHighlight(enabled: Boolean) {
        dataStoreSource.setPinnedNotificationHighlight(enabled)
    }

    override val notificationTimeRelative: Flow<Boolean> = dataStoreSource.notificationTimeRelative

    override suspend fun setNotificationTimeRelative(useRelative: Boolean) {
        dataStoreSource.setNotificationTimeRelative(useRelative)
    }

    override val highlightColor: Flow<String> = dataStoreSource.highlightColor

    override suspend fun setHighlightColor(color: String) {
        dataStoreSource.setHighlightColor(color)
    }

    override val fontScale: Flow<Float> = dataStoreSource.fontScale

    override suspend fun setFontScale(scale: Float) {
        dataStoreSource.setFontScale(scale)
    }
}
