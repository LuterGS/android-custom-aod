package dev.lutergs.sgaod.di

import android.content.Context
import dev.lutergs.sgaod.data.source.local.AlbumArtStore
import dev.lutergs.sgaod.data.source.local.DataStoreSource
import dev.lutergs.sgaod.data.source.local.InMemoryNotificationSource
import dev.lutergs.sgaod.data.source.local.MediaSessionDataSource
import dev.lutergs.sgaod.data.source.local.NotificationIconStore
import dev.lutergs.sgaod.data.source.local.PendingIntentStore
import dev.lutergs.sgaod.data.source.local.SensorDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 애플리케이션 수명 동안 유지되는 CoroutineScope qualifier.
 * 액티비티/리시버 수명주기에 묶이면 안 되는 작업(설정 토글 등)에 사용.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * 애플리케이션 레벨의 의존성을 제공하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideDataStoreSource(
        @ApplicationContext context: Context
    ): DataStoreSource {
        return DataStoreSource(context)
    }

    @Provides
    @Singleton
    fun provideInMemoryNotificationSource(): InMemoryNotificationSource {
        return InMemoryNotificationSource()
    }

    @Provides
    @Singleton
    fun provideSensorDataSource(
        @ApplicationContext context: Context
    ): SensorDataSource {
        return SensorDataSource(context)
    }

    @Provides
    @Singleton
    fun provideAlbumArtStore(): AlbumArtStore {
        return AlbumArtStore()
    }

    @Provides
    @Singleton
    fun provideMediaSessionDataSource(
        @ApplicationContext context: Context,
        albumArtStore: AlbumArtStore
    ): MediaSessionDataSource {
        return MediaSessionDataSource(context, albumArtStore)
    }

    @Provides
    @Singleton
    fun providePendingIntentStore(): PendingIntentStore {
        return PendingIntentStore()
    }

    @Provides
    @Singleton
    fun provideNotificationIconStore(): NotificationIconStore {
        return NotificationIconStore()
    }
}
