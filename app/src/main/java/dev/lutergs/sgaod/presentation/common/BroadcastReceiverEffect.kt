package dev.lutergs.sgaod.presentation.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 컴포지션 수명에 맞춰 BroadcastReceiver 를 등록/해제하는 Effect.
 *
 * - targetSdk 34+ 요건에 따라 RECEIVER_NOT_EXPORTED 플래그를 명시한다
 *   (시스템 브로드캐스트는 플래그와 무관하게 수신 가능)
 * - onReceive 는 rememberUpdatedState 로 감싸 리컴포지션 후에도 최신 람다가 호출된다
 */
@Composable
fun BroadcastReceiverEffect(
    vararg actions: String,
    tag: String = "BroadcastReceiverEffect",
    onReceive: (Intent) -> Unit
) {
    val context = LocalContext.current
    val currentOnReceive by rememberUpdatedState(onReceive)

    DisposableEffect(Unit) {
        val appContext = context.applicationContext
        var isReceiverRegistered = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                currentOnReceive(intent)
            }
        }

        val filter = IntentFilter().apply {
            actions.forEach { addAction(it) }
        }

        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to register receiver", e)
        }

        onDispose {
            if (isReceiverRegistered) {
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to unregister receiver", e)
                }
            }
        }
    }
}
