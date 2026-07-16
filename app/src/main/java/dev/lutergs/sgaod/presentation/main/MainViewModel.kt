package dev.lutergs.sgaod.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.sgaod.domain.model.AppInfo
import dev.lutergs.sgaod.domain.usecase.app.GetAllInstalledAppsUseCase
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsUseCases: SettingsUseCases,
    getAllInstalledAppsUseCase: GetAllInstalledAppsUseCase
) : ViewModel() {

    // 초기값 null = 아직 로드 전. Boolean 초기값(false)을 쓰면 실제 설정이 true 여도
    // 화면 진입 순간 stopService 가 호출되는 결함이 있어 로딩 상태를 구분한다.
    val aodEnabled: StateFlow<Boolean?> = settingsUseCases.aodEnabled
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val maxNotificationCount: StateFlow<Int> = settingsUseCases.maxNotificationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val maxPinnedNotificationCount: StateFlow<Int> = settingsUseCases.maxPinnedNotificationCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val pinnedApps: StateFlow<Set<String>> = settingsUseCases.pinnedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allInstalledApps: StateFlow<List<AppInfo>> = getAllInstalledAppsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeFormat24h: StateFlow<Boolean> = settingsUseCases.timeFormat24h
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pinnedNotificationHighlight: StateFlow<Boolean> = settingsUseCases.pinnedNotificationHighlight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationTimeRelative: StateFlow<Boolean> = settingsUseCases.notificationTimeRelative
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val highlightColor: StateFlow<String> = settingsUseCases.highlightColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "FFD700")

    val fontScale: StateFlow<Float> = settingsUseCases.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    fun setAodEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setAodEnabled(enabled)
        }
    }

    fun setMaxNotificationCount(count: Int) {
        viewModelScope.launch {
            settingsUseCases.setMaxNotificationCount(count)
        }
    }

    fun setMaxPinnedNotificationCount(count: Int) {
        viewModelScope.launch {
            settingsUseCases.setMaxPinnedNotificationCount(count)
        }
    }

    fun addPinnedApp(packageName: String) {
        viewModelScope.launch {
            settingsUseCases.addPinnedApp(packageName)
        }
    }

    fun removePinnedApp(packageName: String) {
        viewModelScope.launch {
            settingsUseCases.removePinnedApp(packageName)
        }
    }

    fun setTimeFormat24h(use24h: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setTimeFormat24h(use24h)
        }
    }

    fun setPinnedNotificationHighlight(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setPinnedNotificationHighlight(enabled)
        }
    }

    fun setNotificationTimeRelative(useRelative: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setNotificationTimeRelative(useRelative)
        }
    }

    fun setHighlightColor(color: String) {
        viewModelScope.launch {
            settingsUseCases.setHighlightColor(color)
        }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            settingsUseCases.setFontScale(scale)
        }
    }
}
