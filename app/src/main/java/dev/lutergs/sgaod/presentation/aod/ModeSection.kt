package dev.lutergs.sgaod.presentation.aod

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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

data class ModeInfo(
    val isDndEnabled: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL
)

private fun getRingerModeText(ringerMode: Int): String? {
    return when (ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "무음"
        AudioManager.RINGER_MODE_VIBRATE -> "진동"
        else -> null
    }
}

@Composable
fun ModeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationManager = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var modeInfo by remember { mutableStateOf(ModeInfo()) }

    fun updateModeInfo() {
        val isDnd =
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val ringerMode = audioManager.ringerMode

        modeInfo = ModeInfo(
            isDndEnabled = isDnd,
            ringerMode = ringerMode
        )
    }

    DisposableEffect(Unit) {
        val appContext = context.applicationContext
        var isReceiverRegistered = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                updateModeInfo()
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }

        try {
            appContext.registerReceiver(receiver, filter)
            isReceiverRegistered = true
        } catch (e: Exception) {
            android.util.Log.e("ModeSection", "Failed to register mode receiver", e)
        }

        updateModeInfo()

        onDispose {
            if (isReceiverRegistered) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    android.util.Log.e("ModeSection", "Receiver already unregistered", e)
                } catch (e: Exception) {
                    android.util.Log.e("ModeSection", "Failed to unregister mode receiver", e)
                }
            }
        }
    }

    val dndText = if (modeInfo.isDndEnabled) "방해 금지" else null
    val ringerText = getRingerModeText(modeInfo.ringerMode)

    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        when {
            dndText != null && ringerText != null -> {
                Text(
                    text = dndText,
                    color = AodPalette.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = ringerText,
                    color = AodPalette.secondaryText,
                    fontSize = 12.sp
                )
            }
            dndText != null -> {
                Text(
                    text = dndText,
                    color = AodPalette.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            ringerText != null -> {
                Text(
                    text = ringerText,
                    color = AodPalette.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            else -> {
                Text(
                    text = "-",
                    color = AodPalette.tertiaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
