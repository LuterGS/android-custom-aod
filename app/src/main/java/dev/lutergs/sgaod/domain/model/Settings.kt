package dev.lutergs.sgaod.domain.model

/**
 * AOD 설정을 담는 도메인 모델
 */
data class Settings(
    val aodEnabled: Boolean = false,
    val maxNotificationCount: Int = 4,
    val excludedApps: Set<String> = emptySet(),
    val pinnedApps: Set<String> = emptySet()
) {
    companion object {
        const val MIN_NOTIFICATION_COUNT = 1
        const val MAX_NOTIFICATION_COUNT = 10
        const val MAX_PINNED_APPS = 5
        const val MIN_PINNED_NOTIFICATION_COUNT = 1
        const val MAX_PINNED_NOTIFICATION_COUNT = 5
        const val DEFAULT_HIGHLIGHT_COLOR = "FFD700"
        const val MIN_FONT_SCALE = 0.5f
        const val MAX_FONT_SCALE = 2.0f
        const val DEFAULT_FONT_SCALE = 1.0f
    }
}
