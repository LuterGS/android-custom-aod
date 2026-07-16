package dev.lutergs.sgaod.domain.usecase.media

import dev.lutergs.sgaod.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * 미디어 재생 제어를 담당하는 UseCase
 */
class ControlMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    fun play() = mediaRepository.play()

    fun pause() = mediaRepository.pause()

    fun skipToNext() = mediaRepository.skipToNext()

    fun skipToPrevious() = mediaRepository.skipToPrevious()

    fun togglePlayPause() = mediaRepository.togglePlayPause()
}
