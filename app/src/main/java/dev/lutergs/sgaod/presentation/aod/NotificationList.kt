package dev.lutergs.sgaod.presentation.aod

import android.graphics.drawable.Icon
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lutergs.sgaod.domain.model.NotificationGroup
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// DateTimeFormatter 는 스레드 안전 — 파일 레벨에서 1회 생성해 재사용
private val absoluteTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 알림 시간을 포맷하는 함수
 */
private fun formatNotificationTime(postTime: Long, useRelativeTime: Boolean, currentTimeMillis: Long): String {
    return if (useRelativeTime) {
        DateUtils.getRelativeTimeSpanString(
            postTime,
            currentTimeMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } else {
        Instant.ofEpochMilli(postTime)
            .atZone(ZoneId.systemDefault())
            .format(absoluteTimeFormatter)
    }
}

/**
 * 알림 개수 badge 컴포넌트 (outline 스타일 — OLED 점등 픽셀 최소화)
 */
@Composable
private fun NotificationCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .border(1.dp, AodPalette.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = AodPalette.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 알림 아이콘 — loadDrawable 은 Binder IPC + 디코딩 비용이 있으므로
 * 반드시 remember 로 캐싱해 recomposition 시 재호출을 막는다
 */
@Composable
private fun NotificationAppIcon(icon: Icon?, contentDescription: String) {
    val context = LocalContext.current
    val drawable = remember(icon) {
        icon?.let {
            try {
                it.loadDrawable(context.applicationContext)
            } catch (e: Exception) {
                null
            }
        }
    }
    if (drawable != null) {
        Image(
            painter = rememberDrawablePainter(drawable = drawable),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
    }
}

/**
 * 그룹화된 알림 아이템 (Pinned/Regular 통합)
 */
@Composable
private fun GroupedNotificationItemRow(
    group: NotificationGroup,
    onClick: () -> Unit,
    style: NotificationStyle,
    useRelativeTime: Boolean,
    currentTimeMillis: Long,
    icon: Icon?,
    modifier: Modifier = Modifier
) {
    val notification = group.latestNotification

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        NotificationAppIcon(icon = icon, contentDescription = notification.appName)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = notification.appName,
                    color = AodPalette.primaryText.copy(alpha = style.appNameAlpha),
                    fontSize = 13.sp,
                    fontWeight = style.appNameFontWeight
                )

                if (group.hasAdditionalNotifications) {
                    Spacer(modifier = Modifier.width(6.dp))
                    NotificationCountBadge(count = group.totalCount)
                }

                if (notification.isContentVisible && notification.title.isNotEmpty()) {
                    Text(
                        text = "  ${notification.title}",
                        color = AodPalette.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Text(
                    text = formatNotificationTime(notification.postTime, useRelativeTime, currentTimeMillis),
                    color = AodPalette.tertiaryText,
                    fontSize = 12.sp
                )
            }

            if (notification.isContentVisible && notification.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = notification.content,
                    color = AodPalette.primaryText.copy(alpha = style.contentAlpha),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 그룹화된 알림 리스트 섹션 (Pinned/Regular 통합)
 */
@Composable
fun GroupedNotificationListSection(
    notificationGroups: List<NotificationGroup>,
    onNotificationClick: (String) -> Unit,
    style: NotificationStyle,
    modifier: Modifier = Modifier,
    maxCount: Int = Int.MAX_VALUE,
    useRelativeTime: Boolean = true,
    currentTimeMillis: Long = System.currentTimeMillis(),
    getIcon: (String) -> Icon? = { null }
) {
    val limitedGroups = notificationGroups.take(maxCount)

    val borderModifier = if (style.showHighlightBorder) {
        Modifier
            .border(1.5.dp, style.highlightColor, RoundedCornerShape(16.dp))
            .padding(6.dp)
    } else {
        Modifier
    }

    Column(modifier = modifier.then(borderModifier)) {
        limitedGroups.forEachIndexed { index, group ->
            // key(): 행 identity 를 알림 key 에 고정 — 목록 순서가 바뀌어도
            // remember(icon) 캐시가 유지되어 loadDrawable 연쇄 재실행을 방지
            key(group.latestNotification.key) {
                GroupedNotificationItemRow(
                    group = group,
                    onClick = { onNotificationClick(group.latestNotification.key) },
                    style = style,
                    useRelativeTime = useRelativeTime,
                    currentTimeMillis = currentTimeMillis,
                    icon = getIcon(group.latestNotification.key)
                )
            }

            if (index < limitedGroups.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = AodPalette.divider
                )
            }
        }
    }
}
