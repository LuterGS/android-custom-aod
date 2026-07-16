package dev.lutergs.sgaod.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * DataStore를 통한 로컬 설정 저장소
 *
 * 파일 손상/디스크 I/O 오류가 collect 중인 코루틴으로 전파되어
 * 앱이 크래시하지 않도록 안전한 data Flow 를 통해서만 값을 노출한다.
 */
@Singleton
class DataStoreSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // retryWhen: 일시적 I/O 오류 시 기본값을 1회 방출한 뒤 재구독한다.
    // catch+emit 만 쓰면 Flow 가 완료되어 설정 UI 가 기본값으로 영구 동결됨.
    // 파일 손상(CorruptionException)은 corruptionHandler 가 처리한다.
    private val safeData: Flow<Preferences> = context.dataStore.data
        .retryWhen { cause, attempt ->
            if (cause is IOException) {
                emit(emptyPreferences())
                delay(1_000L * (attempt + 1).coerceAtMost(5))
                true
            } else {
                false
            }
        }
    companion object {
        private val AOD_ENABLED = booleanPreferencesKey("aod_enabled")
        private val MAX_NOTIFICATION_COUNT = intPreferencesKey("max_notification_count")
        private val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val PINNED_APPS = stringSetPreferencesKey("pinned_apps")
        private val MAX_PINNED_NOTIFICATION_COUNT = intPreferencesKey("max_pinned_notification_count")
        // 표시 옵션 설정 키
        private val TIME_FORMAT_24H = booleanPreferencesKey("time_format_24h")
        private val PINNED_NOTIFICATION_HIGHLIGHT = booleanPreferencesKey("pinned_notification_highlight")
        private val NOTIFICATION_TIME_RELATIVE = booleanPreferencesKey("notification_time_relative")
        private val HIGHLIGHT_COLOR = stringPreferencesKey("highlight_color")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
    }

    // AOD 활성화 상태
    val aodEnabled: Flow<Boolean> =
        safeData.map { preferences ->
            preferences[AOD_ENABLED] ?: false
        }

    suspend fun setAodEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AOD_ENABLED] = enabled
        }
    }

    // 최대 알림 개수
    val maxNotificationCount: Flow<Int> =
        safeData.map { preferences ->
            preferences[MAX_NOTIFICATION_COUNT] ?: 4
        }

    suspend fun setMaxNotificationCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_NOTIFICATION_COUNT] = count
        }
    }

    // 제외된 앱 목록
    val excludedApps: Flow<Set<String>> =
        safeData.map { preferences ->
            preferences[EXCLUDED_APPS] ?: emptySet()
        }

    suspend fun addExcludedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_APPS] ?: emptySet()
            preferences[EXCLUDED_APPS] = current + packageName
        }
    }

    suspend fun removeExcludedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_APPS] ?: emptySet()
            preferences[EXCLUDED_APPS] = current - packageName
        }
    }

    suspend fun setExcludedApps(apps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[EXCLUDED_APPS] = apps
        }
    }

    // 상단 고정 앱 목록
    val pinnedApps: Flow<Set<String>> =
        safeData.map { preferences ->
            preferences[PINNED_APPS] ?: emptySet()
        }

    suspend fun addPinnedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PINNED_APPS] ?: emptySet()
            preferences[PINNED_APPS] = current + packageName
        }
    }

    suspend fun removePinnedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PINNED_APPS] ?: emptySet()
            preferences[PINNED_APPS] = current - packageName
        }
    }

    suspend fun setPinnedApps(apps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PINNED_APPS] = apps
        }
    }

    // 고정 알림 최대 개수
    val maxPinnedNotificationCount: Flow<Int> =
        safeData.map { preferences ->
            preferences[MAX_PINNED_NOTIFICATION_COUNT] ?: 3
        }

    suspend fun setMaxPinnedNotificationCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_PINNED_NOTIFICATION_COUNT] = count
        }
    }

    // 24시간제 형식 (true: 24시간제, false: AM/PM)
    val timeFormat24h: Flow<Boolean> =
        safeData.map { preferences ->
            preferences[TIME_FORMAT_24H] ?: true
        }

    suspend fun setTimeFormat24h(use24h: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TIME_FORMAT_24H] = use24h
        }
    }

    // 고정 알림 테두리 하이라이트
    val pinnedNotificationHighlight: Flow<Boolean> =
        safeData.map { preferences ->
            preferences[PINNED_NOTIFICATION_HIGHLIGHT] ?: false
        }

    suspend fun setPinnedNotificationHighlight(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PINNED_NOTIFICATION_HIGHLIGHT] = enabled
        }
    }

    // 알림 시간 상대 표시 (true: "n분 전", false: "HH:mm")
    val notificationTimeRelative: Flow<Boolean> =
        safeData.map { preferences ->
            preferences[NOTIFICATION_TIME_RELATIVE] ?: true
        }

    suspend fun setNotificationTimeRelative(useRelative: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_TIME_RELATIVE] = useRelative
        }
    }

    // 하이라이트 테두리 색상 (기본값: 노란색/Gold)
    val highlightColor: Flow<String> =
        safeData.map { preferences ->
            preferences[HIGHLIGHT_COLOR] ?: "FFD700"
        }

    suspend fun setHighlightColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[HIGHLIGHT_COLOR] = color
        }
    }

    // AOD 글꼴 배율 (1.0 = 100%)
    val fontScale: Flow<Float> =
        safeData.map { preferences ->
            preferences[FONT_SCALE] ?: 1.0f
        }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SCALE] = scale
        }
    }
}
