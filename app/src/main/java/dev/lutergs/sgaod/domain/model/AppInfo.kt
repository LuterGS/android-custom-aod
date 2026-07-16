package dev.lutergs.sgaod.domain.model

/**
 * 설치된 앱 정보를 나타내는 도메인 모델
 */
data class AppInfo(
    val packageName: String,
    val appName: String
)
