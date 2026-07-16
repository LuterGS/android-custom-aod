package dev.lutergs.sgaod.domain.usecase.app

import dev.lutergs.sgaod.domain.model.AppInfo
import dev.lutergs.sgaod.domain.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 설치된 모든 앱 목록을 가져오는 UseCase
 */
class GetAllInstalledAppsUseCase @Inject constructor(
    private val appRepository: AppRepository
) {
    operator fun invoke(): Flow<List<AppInfo>> {
        return appRepository.getAllInstalledApps()
    }
}
