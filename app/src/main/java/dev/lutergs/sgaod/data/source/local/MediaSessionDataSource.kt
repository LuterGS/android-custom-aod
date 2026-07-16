package dev.lutergs.sgaod.data.source.local

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.core.graphics.scale
import dev.lutergs.sgaod.domain.model.MediaInfo
import dev.lutergs.sgaod.service.AODNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * MediaSession을 통해 현재 재생 중인 미디어 정보를 제공하는 DataSource
 *
 * 참고: NotificationListenerService 권한이 필요함 (AODNotificationListener)
 * 앨범 아트(Bitmap)는 AlbumArtStore에서 별도 관리
 */
class MediaSessionDataSource @Inject constructor(
    private val context: Context,
    private val albumArtStore: AlbumArtStore
) {
    private val mediaSessionManager: MediaSessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val _mediaInfo = MutableStateFlow(MediaInfo.empty())
    val mediaInfo: StateFlow<MediaInfo> = _mediaInfo.asStateFlow()

    private var activeController: MediaController? = null
    private var sessionCallback: MediaController.Callback? = null
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private val notificationListenerComponent: ComponentName by lazy {
        ComponentName(context, AODNotificationListener::class.java)
    }

    /**
     * 미디어 세션 모니터링 시작
     */
    fun startMonitoring() {
        if (sessionsChangedListener != null) return

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            updateActiveSession(controllers)
        }

        try {
            // NotificationListenerService 권한으로 활성 세션 목록 가져오기
            mediaSessionManager.addOnActiveSessionsChangedListener(
                listener,
                notificationListenerComponent
            )
            // 등록 성공 후에만 필드에 할당 — 실패 시 재시도가 가능하도록
            sessionsChangedListener = listener

            // 현재 활성 세션으로 초기화
            val activeSessions = mediaSessionManager.getActiveSessions(notificationListenerComponent)
            updateActiveSession(activeSessions)
        } catch (e: SecurityException) {
            // 알림 접근 권한 미허용 상태 — 권한 허용 후 startMonitoring 재호출로 복구 가능
            Log.e("MediaSessionDataSource", "Failed to start monitoring (permission not granted?)", e)
        } catch (e: Exception) {
            Log.e("MediaSessionDataSource", "Failed to start monitoring", e)
        }
    }

    /**
     * 미디어 세션 모니터링 중지
     */
    fun stopMonitoring() {
        sessionsChangedListener?.let {
            mediaSessionManager.removeOnActiveSessionsChangedListener(it)
            sessionsChangedListener = null
        }
        unregisterCallback()
        _mediaInfo.value = MediaInfo.empty()
        albumArtStore.clear()
    }

    /**
     * 재생
     */
    fun play() {
        activeController?.transportControls?.play()
    }

    /**
     * 일시정지
     */
    fun pause() {
        activeController?.transportControls?.pause()
    }

    /**
     * 다음 곡
     */
    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    /**
     * 이전 곡
     */
    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    /**
     * 재생/일시정지 토글
     */
    fun togglePlayPause() {
        val playbackState = activeController?.playbackState?.state
        if (playbackState == PlaybackState.STATE_PLAYING) {
            pause()
        } else {
            play()
        }
    }

    private fun updateActiveSession(controllers: List<MediaController>?) {
        // 재생 중인 세션 우선 선택
        val playingController = controllers?.firstOrNull { controller ->
            controller.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        // 재생 중인 것이 없으면 첫 번째 세션 선택
        val selectedController = playingController ?: controllers?.firstOrNull()

        if (selectedController != activeController) {
            unregisterCallback()
            activeController = selectedController
            registerCallback()
        }

        updateMediaInfo()
    }

    private fun registerCallback() {
        activeController?.let { controller ->
            sessionCallback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    updateMediaInfo()
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    updateMediaInfo()
                }

                override fun onSessionDestroyed() {
                    unregisterCallback()
                    _mediaInfo.value = MediaInfo.empty()
                    albumArtStore.clear()
                }
            }
            controller.registerCallback(sessionCallback!!)
        }
    }

    private fun unregisterCallback() {
        sessionCallback?.let { callback ->
            activeController?.unregisterCallback(callback)
            sessionCallback = null
        }
        activeController = null
    }

    private fun updateMediaInfo() {
        val controller = activeController
        if (controller == null) {
            _mediaInfo.value = MediaInfo.empty()
            albumArtStore.clear()
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val packageName = controller.packageName ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L

        // 앨범 아트는 표시 크기에 맞게 다운스케일 후 AlbumArtStore에 저장
        // (원본 1024px+ Bitmap 을 싱글턴에 상주시키면 수 MB 힙을 계속 점유)
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        albumArtStore.update(albumArt?.let { downscaleIfNeeded(it) })

        _mediaInfo.value = MediaInfo(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            packageName = packageName,
            duration = duration,
            position = position
        )
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension <= MAX_ALBUM_ART_SIZE_PX) return bitmap

        val scale = MAX_ALBUM_ART_SIZE_PX.toFloat() / maxDimension
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return try {
            bitmap.scale(targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e("MediaSessionDataSource", "Failed to downscale album art", e)
            bitmap
        }
    }

    companion object {
        private const val MAX_ALBUM_ART_SIZE_PX = 512
    }
}
