package dev.lutergs.sgaod.presentation.aod

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lutergs.sgaod.presentation.common.BroadcastReceiverEffect
import java.time.LocalDateTime

/**
 * 번인 방지 픽셀 시프트: 분 단위 tick 을 삼각파 경로로 변환.
 * 가로는 1px/분, 세로는 1px/3분 속도로 왕복해 콘텐츠 전체가
 * 수 시간에 걸쳐 원위치에서 완전히 벗어난다.
 */
private fun burnInOffset(tick: Int): IntOffset {
    val range = AodConstants.BURN_IN_MAX_OFFSET_PX
    val period = 2 * range
    fun triangle(v: Int): Int = if (v < range) v else period - v
    return IntOffset(
        x = triangle(tick % period),
        y = triangle((tick / 3) % period)
    )
}

private fun parseHighlightColor(hex: String): Color =
    runCatching { Color("#$hex".toColorInt()) }.getOrDefault(Color(0xFFFFD700))

@Composable
fun AODScreen(
    viewModel: AODViewModel,
    isBlackMode: Boolean = false,
    onExitBlackMode: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    // AOD 화면에서 back 제스처를 소비 (targetSdk 36 predictive back 대응 —
    // 실수로 화면이 닫히는 것을 방지, 종료는 더블 탭으로만)
    BackHandler { /* consume */ }

    // 완전 블랙 모드: 아무것도 그리지 않음 (OLED 픽셀 완전 소등)
    if (isBlackMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AodPalette.background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onExitBlackMode() })
                }
        )
        return
    }

    val groupedPinnedNotifications by viewModel.groupedPinnedNotifications.collectAsStateWithLifecycle()
    val groupedRegularNotifications by viewModel.groupedRegularNotifications.collectAsStateWithLifecycle()
    val mediaInfo by viewModel.mediaInfo.collectAsStateWithLifecycle()
    val albumArt by viewModel.albumArt.collectAsStateWithLifecycle()
    val timeFormat24h by viewModel.timeFormat24h.collectAsStateWithLifecycle()
    val pinnedNotificationHighlight by viewModel.pinnedNotificationHighlight.collectAsStateWithLifecycle()
    val notificationTimeRelative by viewModel.notificationTimeRelative.collectAsStateWithLifecycle()
    val highlightColorHex by viewModel.highlightColor.collectAsStateWithLifecycle()
    val highlightColor = remember(highlightColorHex) { parseHighlightColor(highlightColorHex) }
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()

    // 센서 상태는 delegate 없이 State 로 유지 — graphicsLayer 람다 안에서만 읽어
    // 조도 변화가 recomposition 없이 draw 단계에서만 반영되도록 한다 (deferred read)
    val sensorState = viewModel.sensorState.collectAsStateWithLifecycle()

    var lastTapTime by remember { mutableLongStateOf(0L) }

    // 단일 시간 소스: ACTION_TIME_TICK (시스템이 매 분 정각에 발송)
    // 시계 표시와 "n분 전" 상대시간이 같은 tick 을 공유한다
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var minuteTick by remember { mutableIntStateOf(0) }

    BroadcastReceiverEffect(
        Intent.ACTION_TIME_TICK,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        tag = "AODTimeTick"
    ) {
        now = LocalDateTime.now()
        currentTimeMillis = System.currentTimeMillis()
        minuteTick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AodPalette.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < AodConstants.DOUBLE_TAP_THRESHOLD_MS) {
                            onDoubleTap()
                        }
                        lastTapTime = currentTime
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 번인 방지: placement 블록 안에서만 상태를 읽어
                // 매분 tick 이 재측정 없이 placement-only 무효화가 되도록 한다.
                // 콘텐츠는 시프트 최대 범위만큼 작게 측정해, 어떤 오프셋에서도
                // 화면 밖으로 짤리지 않도록 여유 공간을 예약한다.
                .layout { measurable, constraints ->
                    val range = AodConstants.BURN_IN_MAX_OFFSET_PX
                    val reducedWidth = (constraints.maxWidth - range).coerceAtLeast(0)
                    val reducedHeight = (constraints.maxHeight - range).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = minOf(constraints.minWidth, reducedWidth),
                            maxWidth = reducedWidth,
                            minHeight = minOf(constraints.minHeight, reducedHeight),
                            maxHeight = reducedHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val shift = burnInOffset(minuteTick)
                        placeable.place(shift.x, shift.y)
                    }
                }
                // 조도/수면 모드에 따른 디밍: draw 단계에서만 상태를 읽어 갱신
                .graphicsLayer {
                    val state = sensorState.value
                    alpha = if (state.isSleepMode) 0f else luxToAlpha(state.lux)
                }
        ) {
            // 사용자 글꼴 배율: LocalDensity 의 fontScale 을 오버라이드해
            // AOD 내 모든 sp 단위 텍스트가 일괄 스케일되도록 한다
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale * fontScale
                )
            ) {
            ModeSection(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 28.dp, start = 28.dp)
            )

            BatterySection(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DateTimeSection(
                    dateTime = now,
                    use24HourFormat = timeFormat24h
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 고정 앱 알림 (상단) - 앱별 그룹화
                if (groupedPinnedNotifications.isNotEmpty()) {
                    GroupedNotificationListSection(
                        notificationGroups = groupedPinnedNotifications,
                        onNotificationClick = { key ->
                            viewModel.getPendingIntent(key)?.let { pendingIntent ->
                                try {
                                    pendingIntent.send()
                                    onDoubleTap()
                                } catch (e: Exception) {
                                    Log.e("AODScreen", "Failed to send PendingIntent", e)
                                }
                            }
                        },
                        style = NotificationStyle.pinned(
                            showBorder = pinnedNotificationHighlight,
                            highlightColor = highlightColor
                        ),
                        modifier = Modifier.widthIn(max = 480.dp),
                        useRelativeTime = notificationTimeRelative,
                        currentTimeMillis = currentTimeMillis,
                        getIcon = { key -> viewModel.getNotificationIcon(key) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 일반 알림 (하단) - 앱별 그룹화
                GroupedNotificationListSection(
                    notificationGroups = groupedRegularNotifications,
                    onNotificationClick = { key ->
                        viewModel.getPendingIntent(key)?.let { pendingIntent ->
                            try {
                                pendingIntent.send()
                                onDoubleTap()
                            } catch (e: Exception) {
                                Log.e("AODScreen", "Failed to send PendingIntent", e)
                            }
                        }
                    },
                    style = NotificationStyle.regular(),
                    modifier = Modifier.widthIn(max = 480.dp),
                    useRelativeTime = notificationTimeRelative,
                    currentTimeMillis = currentTimeMillis,
                    getIcon = { key -> viewModel.getNotificationIcon(key) }
                )
            }

            // 미디어 컨트롤러 (하단)
            MediaControllerSection(
                mediaInfo = mediaInfo,
                albumArt = albumArt,
                onPlayPauseClick = { viewModel.onPlayPauseClick() },
                onSkipNextClick = { viewModel.onSkipNextClick() },
                onSkipPreviousClick = { viewModel.onSkipPreviousClick() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 480.dp)
            )
            }
        }
    }
}
