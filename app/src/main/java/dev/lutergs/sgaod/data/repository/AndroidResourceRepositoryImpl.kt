package dev.lutergs.sgaod.data.repository

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import dev.lutergs.sgaod.data.source.local.AlbumArtStore
import dev.lutergs.sgaod.data.source.local.NotificationIconStore
import dev.lutergs.sgaod.data.source.local.PendingIntentStore
import dev.lutergs.sgaod.domain.repository.AndroidResourceRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidResourceRepositoryImpl @Inject constructor(
    private val pendingIntentStore: PendingIntentStore,
    private val notificationIconStore: NotificationIconStore,
    private val albumArtStore: AlbumArtStore
) : AndroidResourceRepository {
    override fun getPendingIntent(key: String): PendingIntent? = pendingIntentStore.get(key)
    override fun getNotificationIcon(key: String): Icon? = notificationIconStore.get(key)
    override val albumArt: StateFlow<Bitmap?> = albumArtStore.currentAlbumArt
}
