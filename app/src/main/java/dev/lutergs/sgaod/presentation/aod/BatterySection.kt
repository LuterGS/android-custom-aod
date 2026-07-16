package dev.lutergs.sgaod.presentation.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlin.math.abs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChargeType {
    NONE,
    USB,
    AC,
    FAST,
    SUPER_FAST,
    WIRELESS
}

data class BatteryInfo(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val chargeType: ChargeType = ChargeType.NONE,
    val timeToFullMinutes: Int = -1
)

private const val TAG = "BatterySection"

/**
 * 전류 기반 충전 타입 판정에 히스테리시스 적용 — 임계 부근에서 순간 전류가
 * 요동칠 때 '고속 충전'/'초고속 충전' 라벨이 매 브로드캐스트마다 뒤집히는 것을 방지.
 * 상향은 원래 임계값, 하향은 임계값의 90% 미만일 때만 강등한다.
 */
private fun resolveCurrentBasedType(absCurrentUa: Int, previousType: ChargeType): ChargeType {
    val superFastUp = BatteryConstants.SUPER_FAST_CHARGING_THRESHOLD_UA
    val superFastDown = (superFastUp * 0.9).toInt()
    val fastUp = BatteryConstants.FAST_CHARGING_THRESHOLD_UA
    val fastDown = (fastUp * 0.9).toInt()

    return when {
        absCurrentUa >= superFastUp -> ChargeType.SUPER_FAST
        previousType == ChargeType.SUPER_FAST && absCurrentUa >= superFastDown -> ChargeType.SUPER_FAST
        absCurrentUa >= fastUp -> ChargeType.FAST
        previousType == ChargeType.FAST && absCurrentUa >= fastDown -> ChargeType.FAST
        else -> ChargeType.NONE  // 호출자가 plugged 기반 타입으로 대체
    }
}

private fun parseBatteryIntent(
    intent: Intent,
    batteryManager: BatteryManager,
    previousInfo: BatteryInfo
): BatteryInfo {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0

    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

    val currentNowUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    val absCurrentUa = abs(currentNowUa)

    val currentBasedType = resolveCurrentBasedType(absCurrentUa, previousInfo.chargeType)
    val chargeType = when {
        !isCharging -> ChargeType.NONE
        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeType.WIRELESS
        currentBasedType != ChargeType.NONE -> currentBasedType
        plugged == BatteryManager.BATTERY_PLUGGED_USB -> ChargeType.USB
        else -> ChargeType.AC
    }

    val chargeTimeRemainingMs = if (isCharging) {
        batteryManager.computeChargeTimeRemaining()
    } else {
        -1L
    }
    val timeToFullMinutes = if (chargeTimeRemainingMs > 0) {
        (chargeTimeRemainingMs / 60000).toInt()
    } else {
        -1
    }

    return BatteryInfo(
        percentage = percentage,
        isCharging = isCharging,
        chargeType = chargeType,
        timeToFullMinutes = timeToFullMinutes
    )
}

private fun getChargingIcon(chargeType: ChargeType): String {
    return when (chargeType) {
        ChargeType.NONE -> ""
        else -> "⚡"  // 모든 충전 타입에서 동일한 아이콘
    }
}

private fun getChargeTypeLabel(chargeType: ChargeType): String? {
    return when (chargeType) {
        ChargeType.FAST -> "고속 충전"
        ChargeType.SUPER_FAST -> "초고속 충전"
        ChargeType.WIRELESS -> "무선 충전"
        else -> null
    }
}

private fun formatTimeToFull(minutes: Int): String? {
    if (minutes <= 0) return null
    return when {
        minutes < 60 -> "${minutes}분 후 완충"
        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes > 0) {
                "${hours}시간 ${remainingMinutes}분 후 완충"
            } else {
                "${hours}시간 후 완충"
            }
        }
    }
}

@Composable
fun BatterySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val batteryManager = remember {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    var batteryInfo by remember { mutableStateOf(BatteryInfo()) }

    DisposableEffect(Unit) {
        val appContext = context.applicationContext
        var isReceiverRegistered = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // ACTION_BATTERY_CHANGED 는 충전 중 매우 빈번 — 값이 실제로 바뀐 경우에만 갱신
                val newInfo = parseBatteryIntent(intent, batteryManager, batteryInfo)
                if (newInfo != batteryInfo) {
                    batteryInfo = newInfo
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        try {
            val initialIntent = appContext.registerReceiver(receiver, filter)
            isReceiverRegistered = true
            initialIntent?.let {
                batteryInfo = parseBatteryIntent(it, batteryManager, batteryInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver", e)
        }

        onDispose {
            if (isReceiverRegistered) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Receiver already unregistered", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister battery receiver", e)
                }
            }
        }
    }

    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.End
    ) {
        val icon = getChargingIcon(batteryInfo.chargeType)
        val percentageText = if (icon.isNotEmpty()) {
            "$icon ${batteryInfo.percentage}%"
        } else {
            "${batteryInfo.percentage}%"
        }

        Text(
            text = percentageText,
            color = if (batteryInfo.isCharging) AodPalette.chargingAccent else AodPalette.primaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        getChargeTypeLabel(batteryInfo.chargeType)?.let { label ->
            Text(
                text = label,
                color = AodPalette.secondaryText,
                fontSize = 12.sp
            )
        }

        if (batteryInfo.isCharging) {
            formatTimeToFull(batteryInfo.timeToFullMinutes)?.let { timeText ->
                Text(
                    text = timeText,
                    color = AodPalette.secondaryText,
                    fontSize = 12.sp
                )
            }
        }
    }
}
