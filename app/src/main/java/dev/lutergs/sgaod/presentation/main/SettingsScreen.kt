package dev.lutergs.sgaod.presentation.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lutergs.sgaod.service.AODService
import kotlin.math.roundToInt

private fun parseColorOrDefault(hex: String): Color =
    runCatching { Color("#$hex".toColorInt()) }.getOrDefault(Color(0xFFFFD700))

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun SliderSettingItem(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit
) {
    ListItem(
        headlineContent = { Text("$title: $value") },
        supportingContent = {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

/**
 * 글꼴 배율 설정: 슬라이더(50~200%) + 직접 숫자 입력.
 * 텍스트 필드는 편집 중 임시 상태를 유지하고, 유효 범위 값만 반영한다.
 */
@Composable
private fun FontScaleSettingItem(
    scalePercent: Int,
    onScalePercentChange: (Int) -> Unit
) {
    var textValue by remember(scalePercent) { mutableStateOf(scalePercent.toString()) }

    ListItem(
        headlineContent = { Text("글꼴 크기: ${scalePercent}%") },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = scalePercent.toFloat(),
                    onValueChange = { onScalePercentChange(it.roundToInt()) },
                    valueRange = MIN_FONT_SCALE_PERCENT.toFloat()..MAX_FONT_SCALE_PERCENT.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input.filter { it.isDigit() }.take(3)
                        textValue.toIntOrNull()?.let { value ->
                            if (value in MIN_FONT_SCALE_PERCENT..MAX_FONT_SCALE_PERCENT) {
                                onScalePercentChange(value)
                            }
                        }
                    },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    )
}

private const val MIN_FONT_SCALE_PERCENT = 50
private const val MAX_FONT_SCALE_PERCENT = 200

@Composable
private fun NavigationSettingItem(
    title: String,
    description: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onExcludedAppsClick: () -> Unit = {},
    onPinnedAppsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val aodEnabled by viewModel.aodEnabled.collectAsStateWithLifecycle()
    val maxNotificationCount by viewModel.maxNotificationCount.collectAsStateWithLifecycle()
    val maxPinnedNotificationCount by viewModel.maxPinnedNotificationCount.collectAsStateWithLifecycle()
    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val timeFormat24h by viewModel.timeFormat24h.collectAsStateWithLifecycle()
    val pinnedNotificationHighlight by viewModel.pinnedNotificationHighlight.collectAsStateWithLifecycle()
    val notificationTimeRelative by viewModel.notificationTimeRelative.collectAsStateWithLifecycle()
    val highlightColorHex by viewModel.highlightColor.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    var showColorPicker by remember { mutableStateOf(false) }

    // 서비스 제어는 DataStore 값 변경에만 반응하는 단일 경로로 일원화.
    // 초기값 null(로딩 전)은 무시하므로 이전 구현의 '진입 시 서비스 잠깐 중단' 결함이 없고,
    // 수동 start/stop 과 반응형 start 가 섞여 생기는 경합도 제거된다.
    LaunchedEffect(aodEnabled) {
        val intent = Intent(context, AODService::class.java)
        when (aodEnabled) {
            true -> context.startForegroundService(intent)
            false -> context.stopService(intent)
            null -> Unit
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("AOD 설정") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SwitchSettingItem(
                title = "AOD 사용",
                description = "화면이 꺼지면 커스텀 AOD를 표시합니다",
                checked = aodEnabled == true,
                onCheckedChange = { viewModel.setAodEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader("알림")

            SliderSettingItem(
                title = "일반 알림 최대 개수",
                value = maxNotificationCount,
                valueRange = 1f..10f,
                steps = 8,
                onValueChange = { viewModel.setMaxNotificationCount(it) }
            )

            SliderSettingItem(
                title = "고정 알림 최대 개수",
                value = maxPinnedNotificationCount,
                valueRange = 1f..5f,
                steps = 3,
                onValueChange = { viewModel.setMaxPinnedNotificationCount(it) }
            )

            NavigationSettingItem(
                title = "알림 제외 앱",
                description = "AOD에 표시하지 않을 앱을 선택합니다",
                onClick = onExcludedAppsClick
            )

            NavigationSettingItem(
                title = "상단 고정 앱",
                description = "${pinnedApps.size}/5개 선택됨",
                onClick = onPinnedAppsClick
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader("표시 옵션")

            SwitchSettingItem(
                title = "24시간제 형식",
                description = if (timeFormat24h) "14:30" else "오후 2:30",
                checked = timeFormat24h,
                onCheckedChange = { viewModel.setTimeFormat24h(it) }
            )

            SwitchSettingItem(
                title = "고정 알림 테두리",
                description = "고정 알림에 테두리 박스 표시",
                checked = pinnedNotificationHighlight,
                onCheckedChange = { viewModel.setPinnedNotificationHighlight(it) }
            )

            if (pinnedNotificationHighlight) {
                ListItem(
                    headlineContent = { Text("테두리 색상") },
                    supportingContent = { Text("#$highlightColorHex") },
                    trailingContent = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseColorOrDefault(highlightColorHex))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    },
                    modifier = Modifier.clickable { showColorPicker = true }
                )
            }

            SwitchSettingItem(
                title = "알림 시간 상대 표시",
                description = if (notificationTimeRelative) "5분 전" else "14:30",
                checked = notificationTimeRelative,
                onCheckedChange = { viewModel.setNotificationTimeRelative(it) }
            )

            FontScaleSettingItem(
                scalePercent = (fontScale * 100).roundToInt(),
                onScalePercentChange = { percent ->
                    viewModel.setFontScale(percent / 100f)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = highlightColorHex,
            onColorSelected = { color ->
                viewModel.setHighlightColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}
