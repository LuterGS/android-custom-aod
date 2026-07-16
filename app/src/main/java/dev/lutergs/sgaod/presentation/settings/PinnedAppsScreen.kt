package dev.lutergs.sgaod.presentation.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.lutergs.sgaod.presentation.common.AppSelectionScreen
import dev.lutergs.sgaod.presentation.main.MainViewModel

@Composable
fun PinnedAppsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val pinnedApps by viewModel.pinnedApps.collectAsStateWithLifecycle()
    val allInstalledApps by viewModel.allInstalledApps.collectAsStateWithLifecycle()

    AppSelectionScreen(
        title = "상단 고정 앱 (${pinnedApps.size}/5)",
        description = "설치된 앱 중에서 선택하세요.\n고정된 앱의 알림은 항상 목록 상단에 표시됩니다.",
        selectedApps = pinnedApps,
        allInstalledApps = allInstalledApps,
        onAppToggle = { packageName, isChecked ->
            if (isChecked) viewModel.addPinnedApp(packageName)
            else viewModel.removePinnedApp(packageName)
        },
        onBack = onBack,
        canSelectMore = pinnedApps.size < 5
    )
}
