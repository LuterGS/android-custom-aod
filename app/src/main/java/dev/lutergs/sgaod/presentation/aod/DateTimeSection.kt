package dev.lutergs.sgaod.presentation.aod

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// DateTimeFormatter 는 스레드 안전 — 파일 레벨에서 1회 생성해 재사용
private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val timeFormatter24h = DateTimeFormatter.ofPattern("HH:mm")
private val timeFormatter12h = DateTimeFormatter.ofPattern("h:mm")
private val amPmFormatter = DateTimeFormatter.ofPattern("a", Locale.KOREAN)

// tabular numbers: 분이 바뀌어도 자릿수 폭이 일정해 시계가 흔들리지 않음
private val clockTextStyle = TextStyle(
    fontFeatureSettings = "tnum"
)

/**
 * 시계 + 날짜 표시 (stateless).
 * 시간 갱신은 상위(AODScreen)의 단일 TIME_TICK 소스에서 내려받는다.
 */
@Composable
fun DateTimeSection(
    dateTime: LocalDateTime,
    modifier: Modifier = Modifier,
    use24HourFormat: Boolean = true
) {
    val dateText = remember(dateTime.toLocalDate()) { dateTime.format(dateFormatter) }
    val timeText = remember(dateTime, use24HourFormat) {
        dateTime.format(if (use24HourFormat) timeFormatter24h else timeFormatter12h)
    }
    val amPmText = if (use24HourFormat) null
    else remember(dateTime, use24HourFormat) { dateTime.format(amPmFormatter) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeText,
                color = AodPalette.primaryText,
                fontSize = 96.sp,
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-2).sp,
                style = clockTextStyle
            )

            amPmText?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = it,
                    color = AodPalette.secondaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = dateText,
            color = AodPalette.secondaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
    }
}
