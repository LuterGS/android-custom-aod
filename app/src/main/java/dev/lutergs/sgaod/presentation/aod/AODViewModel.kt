package dev.lutergs.sgaod.presentation.aod

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.sgaod.domain.model.MediaInfo
import dev.lutergs.sgaod.domain.model.NotificationGroup
import dev.lutergs.sgaod.domain.model.SensorState
import dev.lutergs.sgaod.domain.repository.AndroidResourceRepository
import dev.lutergs.sgaod.domain.repository.MediaRepository
import dev.lutergs.sgaod.domain.repository.NotificationRepository
import dev.lutergs.sgaod.domain.repository.SensorRepository
import dev.lutergs.sgaod.domain.usecase.media.ControlMediaUseCase
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AODViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
    notificationRepository: NotificationRepository,
    private val sensorRepository: SensorRepository,
    private val mediaRepository: MediaRepository,
    private val androidResourceRepository: AndroidResourceRepository,
    private val controlMediaUseCase: ControlMediaUseCase
) : ViewModel() {

    val groupedPinnedNotifications: StateFlow<List<NotificationGroup>> = notificationRepository.groupedPinnedNotifications

    val groupedRegularNotifications: StateFlow<List<NotificationGroup>> = notificationRepository.groupedRegularNotifications

    /**
     * 24시간제 형식 설정
     */
    val timeFormat24h: StateFlow<Boolean> = settingsUseCases.timeFormat24h
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * 고정 알림 테두리 하이라이트 설정
     */
    val pinnedNotificationHighlight: StateFlow<Boolean> = settingsUseCases.pinnedNotificationHighlight
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * 알림 시간 상대 표시 설정
     */
    val notificationTimeRelative: StateFlow<Boolean> = settingsUseCases.notificationTimeRelative
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * 하이라이트 테두리 색상 (HEX 형식)
     */
    val highlightColor: StateFlow<String> = settingsUseCases.highlightColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "FFD700"
        )

    /**
     * AOD 글꼴 배율 (1.0 = 100%)
     */
    val fontScale: StateFlow<Float> = settingsUseCases.fontScale
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1.0f
        )

    /**
     * 센서 상태 (조도, 가속도, 근접)
     */
    val sensorState: StateFlow<SensorState> = sensorRepository.sensorState

    /**
     * 현재 재생 중인 미디어 정보
     */
    val mediaInfo: StateFlow<MediaInfo> = mediaRepository.mediaInfo

    /**
     * 앨범 아트 (Bitmap) - AndroidResourceRepository를 통해 관찰
     */
    val albumArt: StateFlow<Bitmap?> = androidResourceRepository.albumArt

    init {
        // 센서 모니터링 시작
        sensorRepository.startMonitoring()

        // 미디어 세션 모니터링 시작
        mediaRepository.startMonitoring()

        // 근접 센서 상태에 따라 조도/가속도 센서 제어 (배터리 최적화)
        sensorRepository.isProximityClose
            .onEach { isClose ->
                // 근접 감지 시 조도/가속도 센서 비활성화, 해제 시 활성화
                sensorRepository.setSecondarySensorsEnabled(!isClose)
            }
            .launchIn(viewModelScope)
    }

    /**
     * PendingIntent 조회 - AndroidResourceRepository를 통해 조회
     */
    fun getPendingIntent(key: String): PendingIntent? {
        return androidResourceRepository.getPendingIntent(key)
    }

    /**
     * 알림 아이콘 조회 - AndroidResourceRepository를 통해 조회
     */
    fun getNotificationIcon(key: String): Icon? {
        return androidResourceRepository.getNotificationIcon(key)
    }

    /**
     * 재생/일시정지 토글
     */
    fun onPlayPauseClick() {
        controlMediaUseCase.togglePlayPause()
    }

    /**
     * 다음 곡으로 이동
     */
    fun onSkipNextClick() {
        controlMediaUseCase.skipToNext()
    }

    /**
     * 이전 곡으로 이동
     */
    fun onSkipPreviousClick() {
        controlMediaUseCase.skipToPrevious()
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel 종료 시 모니터링 중지
        sensorRepository.stopMonitoring()
        mediaRepository.stopMonitoring()
    }
}
