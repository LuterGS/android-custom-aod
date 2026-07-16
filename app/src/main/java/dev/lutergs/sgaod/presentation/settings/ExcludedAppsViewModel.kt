package dev.lutergs.sgaod.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lutergs.sgaod.domain.model.AppInfo
import dev.lutergs.sgaod.domain.usecase.app.GetAllInstalledAppsUseCase
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExcludedAppsViewModel @Inject constructor(
    private val settingsUseCases: SettingsUseCases,
    getAllInstalledAppsUseCase: GetAllInstalledAppsUseCase
) : ViewModel() {

    val excludedApps: StateFlow<Set<String>> = settingsUseCases.excludedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allInstalledApps: StateFlow<List<AppInfo>> = getAllInstalledAppsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExcludedApp(packageName: String) {
        viewModelScope.launch {
            settingsUseCases.addExcludedApp(packageName)
        }
    }

    fun removeExcludedApp(packageName: String) {
        viewModelScope.launch {
            settingsUseCases.removeExcludedApp(packageName)
        }
    }
}
