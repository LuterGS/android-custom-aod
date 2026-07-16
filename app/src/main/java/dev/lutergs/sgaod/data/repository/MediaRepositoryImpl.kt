package dev.lutergs.sgaod.data.repository

import dev.lutergs.sgaod.data.source.local.MediaSessionDataSource
import dev.lutergs.sgaod.domain.model.MediaInfo
import dev.lutergs.sgaod.domain.repository.MediaRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * MediaRepository 구현체
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaSessionDataSource: MediaSessionDataSource
) : MediaRepository {

    override val mediaInfo: StateFlow<MediaInfo>
        get() = mediaSessionDataSource.mediaInfo

    override fun startMonitoring() {
        mediaSessionDataSource.startMonitoring()
    }

    override fun stopMonitoring() {
        mediaSessionDataSource.stopMonitoring()
    }

    override fun play() {
        mediaSessionDataSource.play()
    }

    override fun pause() {
        mediaSessionDataSource.pause()
    }

    override fun skipToNext() {
        mediaSessionDataSource.skipToNext()
    }

    override fun skipToPrevious() {
        mediaSessionDataSource.skipToPrevious()
    }

    override fun togglePlayPause() {
        mediaSessionDataSource.togglePlayPause()
    }
}
