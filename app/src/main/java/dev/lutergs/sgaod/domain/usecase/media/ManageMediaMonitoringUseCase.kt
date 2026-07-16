package dev.lutergs.sgaod.domain.usecase.media

import dev.lutergs.sgaod.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * 미디어 세션 모니터링을 관리하는 UseCase
 */
class ManageMediaMonitoringUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    fun startMonitoring() = mediaRepository.startMonitoring()

    fun stopMonitoring() = mediaRepository.stopMonitoring()
}
