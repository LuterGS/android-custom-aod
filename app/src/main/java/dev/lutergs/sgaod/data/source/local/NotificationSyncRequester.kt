package dev.lutergs.sgaod.data.source.local

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AOD 표시 시점 등에서 NotificationListenerService 에 전체 재동기화를 요청하는 채널.
 *
 * 리스너가 시스템에 의해 unbind 되어 콜백을 놓친 경우에도,
 * AOD 가 화면에 뜰 때마다 activeNotifications 기준으로 목록을 다시 맞춘다.
 */
@Singleton
class NotificationSyncRequester @Inject constructor() {

    private val _syncRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val syncRequests: SharedFlow<Unit> = _syncRequests.asSharedFlow()

    fun requestSync() {
        _syncRequests.tryEmit(Unit)
    }
}
