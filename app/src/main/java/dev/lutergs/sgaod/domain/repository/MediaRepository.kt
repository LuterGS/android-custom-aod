package dev.lutergs.sgaod.domain.repository

import dev.lutergs.sgaod.domain.model.MediaInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * 미디어 정보 및 제어를 담당하는 Repository 인터페이스
 */
interface MediaRepository {
    /**
     * 현재 재생 중인 미디어 정보
     */
    val mediaInfo: StateFlow<MediaInfo>

    /**
     * 미디어 세션 모니터링 시작
     */
    fun startMonitoring()

    /**
     * 미디어 세션 모니터링 중지
     */
    fun stopMonitoring()

    /**
     * 재생
     */
    fun play()

    /**
     * 일시정지
     */
    fun pause()

    /**
     * 다음 곡으로 이동
     */
    fun skipToNext()

    /**
     * 이전 곡으로 이동
     */
    fun skipToPrevious()

    /**
     * 재생/일시정지 토글
     */
    fun togglePlayPause()
}
