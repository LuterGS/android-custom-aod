package dev.lutergs.sgaod.presentation.aod

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.ln

object AodConstants {
    const val BURN_IN_MAX_OFFSET_PX = 24
    const val DOUBLE_TAP_THRESHOLD_MS = 1000L
    const val AOD_SCREEN_BRIGHTNESS = 0.12f
    const val AOD_SCREEN_BRIGHTNESS_MIN = 0.01f
    const val AOD_SCREEN_BRIGHTNESS_MAX = 0.35f
    const val TOUCH_BLOCK_OFFSET_DP = 56

    /**
     * 조도(lux)를 window 밝기로 변환.
     * 어두운 방(0 lux)에서는 최소 밝기, 밝은 환경(500 lux+)에서는 최대 0.35.
     */
    fun luxToScreenBrightness(lux: Float): Float {
        val normalizedLux = (ln(lux.coerceAtLeast(1f)) / ln(500f)).coerceIn(0f, 1f)
        return AOD_SCREEN_BRIGHTNESS_MIN +
            normalizedLux * (AOD_SCREEN_BRIGHTNESS_MAX - AOD_SCREEN_BRIGHTNESS_MIN)
    }
}

/**
 * AOD 전용 디자인 토큰 (OLED 친화 팔레트).
 *
 * - 배경은 순수 검정(#000000): OLED 픽셀 완전 소등
 * - 텍스트는 순백 대신 오프화이트: halation(번짐) 감소 + 전력 절감
 * - 점등 픽셀 비율(OPR) 15% 이하 목표 — 대면적 solid fill 금지
 */
object AodPalette {
    val background = Color(0xFF000000)
    val primaryText = Color(0xFFE0E0E0)
    val secondaryText = Color(0xFF9A9A9A)
    val tertiaryText = Color(0xFF6A6A6A)
    val divider = Color(0x26FFFFFF)
    val outline = Color(0x33FFFFFF)
    val surfaceTint = Color(0x14FFFFFF)
    val chargingAccent = Color(0xFF7FB4A2)
}

object BatteryConstants {
    const val SUPER_FAST_CHARGING_THRESHOLD_UA = 4_500_000
    const val FAST_CHARGING_THRESHOLD_UA = 1_500_000
}

/**
 * Notification style parameters that differ between Pinned and Regular notifications.
 * This eliminates code duplication in NotificationList.kt
 */
data class NotificationStyle(
    val appNameAlpha: Float,
    val appNameFontWeight: FontWeight,
    val contentAlpha: Float,
    val showHighlightBorder: Boolean = false,
    val highlightColor: Color = Color(0xFFFFD700)
) {
    companion object {
        fun pinned(showBorder: Boolean = false, highlightColor: Color = Color(0xFFFFD700)) = NotificationStyle(
            appNameAlpha = 0.9f,
            appNameFontWeight = FontWeight.SemiBold,
            contentAlpha = 0.85f,
            showHighlightBorder = showBorder,
            highlightColor = highlightColor
        )

        fun regular() = NotificationStyle(
            appNameAlpha = 0.7f,
            appNameFontWeight = FontWeight.Medium,
            contentAlpha = 0.75f
        )
    }
}

fun luxToAlpha(lux: Float): Float {
    val normalizedLux = (ln(lux.coerceAtLeast(1f)) / ln(500f)).coerceIn(0f, 1f)
    return 0.45f + normalizedLux * 0.55f
}
