package dev.lutergs.sgaod.domain.model

/**
 * 알림 정보를 담는 도메인 모델
 *
 * Clean Architecture 준수를 위해 Android Framework 타입을 포함하지 않음
 * Icon은 Data Layer의 NotificationIconStore에서 key로 조회
 */
data class NotificationInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val content: String,
    val postTime: Long,
    val isContentVisible: Boolean = true,  // 잠금화면에서 내용 표시 가능 여부 (VISIBILITY_PUBLIC인 경우 true)
    /**
     * 그룹핑 단위 식별자.
     * 메시징 앱(카카오톡/Slack/Discord 등)의 대화 알림은 채팅방/채널별로 구분되고,
     * 그 외 앱은 packageName 으로 앱 단위 그룹핑된다.
     */
    val conversationKey: String = packageName
)
