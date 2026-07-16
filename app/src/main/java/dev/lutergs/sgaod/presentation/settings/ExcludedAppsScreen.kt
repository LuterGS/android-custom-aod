package dev.lutergs.sgaod.presentation.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.lutergs.sgaod.presentation.common.AppSelectionScreen

@Composable
fun ExcludedAppsScreen(
    onBack: () -> Unit,
    viewModel: ExcludedAppsViewModel = hiltViewModel()
) {
    val excludedApps by viewModel.excludedApps.collectAsStateWithLifecycle()
    val allInstalledApps by viewModel.allInstalledApps.collectAsStateWithLifecycle()

    AppSelectionScreen(
        title = "알림 제외 앱 (${excludedApps.size}개)",
        description = "설치된 앱 중에서 선택하세요.\n제외된 앱의 알림은 AOD에 표시되지 않습니다.",
        selectedApps = excludedApps,
        allInstalledApps = allInstalledApps,
        onAppToggle = { packageName, isChecked ->
            if (isChecked) viewModel.addExcludedApp(packageName)
            else viewModel.removeExcludedApp(packageName)
        },
        onBack = onBack
    )
}
