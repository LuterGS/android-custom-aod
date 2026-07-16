package dev.lutergs.sgaod.domain.repository

import dev.lutergs.sgaod.domain.model.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * 앱 정보 관련 Repository 인터페이스
 */
interface AppRepository {
    /**
     * 설치된 모든 앱 목록을 반환
     * 시스템 앱을 포함하며, 앱 이름으로 정렬됨
     */
    fun getAllInstalledApps(): Flow<List<AppInfo>>
}
