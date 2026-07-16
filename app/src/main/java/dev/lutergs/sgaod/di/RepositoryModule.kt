package dev.lutergs.sgaod.di

import dev.lutergs.sgaod.data.repository.AndroidResourceRepositoryImpl
import dev.lutergs.sgaod.data.repository.AppRepositoryImpl
import dev.lutergs.sgaod.data.repository.MediaRepositoryImpl
import dev.lutergs.sgaod.data.repository.NotificationRepositoryImpl
import dev.lutergs.sgaod.data.repository.SensorRepositoryImpl
import dev.lutergs.sgaod.data.repository.SettingsRepositoryImpl
import dev.lutergs.sgaod.domain.repository.AndroidResourceRepository
import dev.lutergs.sgaod.domain.repository.AppRepository
import dev.lutergs.sgaod.domain.repository.MediaRepository
import dev.lutergs.sgaod.domain.repository.NotificationRepository
import dev.lutergs.sgaod.domain.repository.SensorRepository
import dev.lutergs.sgaod.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 인터페이스와 구현체를 바인딩하는 Hilt 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSensorRepository(
        sensorRepositoryImpl: SensorRepositoryImpl
    ): SensorRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        mediaRepositoryImpl: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        appRepositoryImpl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindAndroidResourceRepository(
        androidResourceRepositoryImpl: AndroidResourceRepositoryImpl
    ): AndroidResourceRepository
}
