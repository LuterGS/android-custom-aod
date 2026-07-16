package dev.lutergs.sgaod.domain.repository

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import kotlinx.coroutines.flow.StateFlow

/**
 * Android Framework 타입(PendingIntent, Icon, Bitmap)을 제공하는 Repository 인터페이스
 * Data Layer의 Store들을 추상화하여 ViewModel에서 Data Layer 직접 참조를 제거
 */
interface AndroidResourceRepository {
    fun getPendingIntent(key: String): PendingIntent?
    fun getNotificationIcon(key: String): Icon?
    val albumArt: StateFlow<Bitmap?>
}
