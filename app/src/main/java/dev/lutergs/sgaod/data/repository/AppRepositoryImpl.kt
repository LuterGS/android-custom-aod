package dev.lutergs.sgaod.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lutergs.sgaod.domain.model.AppInfo
import dev.lutergs.sgaod.domain.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppRepository 구현체
 * PackageManager를 통해 설치된 앱 목록을 조회
 */
@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppRepository {

    override fun getAllInstalledApps(): Flow<List<AppInfo>> = flow {
        val packageManager = context.packageManager
        
        // 런처에서 실행 가능한 앱만 가져오기 (시스템 앱 중 유용한 것들 포함)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        
        val apps = resolveInfoList.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(packageManager).toString()
            
            // 자기 자신(Custom AOD 앱)은 제외
            if (packageName == context.packageName) {
                null
            } else {
                AppInfo(
                    packageName = packageName,
                    appName = appName
                )
            }
        }
            .distinctBy { it.packageName }  // 중복 제거
            .sortedBy { it.appName.lowercase() }  // 앱 이름으로 정렬
        
        emit(apps)
    }.flowOn(Dispatchers.IO)
}
