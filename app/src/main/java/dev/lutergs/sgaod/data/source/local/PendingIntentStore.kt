package dev.lutergs.sgaod.data.source.local

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PendingIntent를 관리하는 Data Layer Store
 * Domain Layer의 순수성을 위해 Android 타입인 PendingIntent를 이 클래스에서 관리
 *
 * NotificationListenerService 콜백 스레드와 UI 스레드에서 동시 접근되므로 ConcurrentHashMap 사용
 */
@Singleton
class PendingIntentStore @Inject constructor() {

    private val pendingIntents = ConcurrentHashMap<String, PendingIntent>()

    fun register(key: String, pendingIntent: PendingIntent) {
        pendingIntents[key] = pendingIntent
    }

    fun get(key: String): PendingIntent? = pendingIntents[key]

    fun remove(key: String) {
        pendingIntents.remove(key)
    }

    /** 활성 알림에 없는 key 를 정리 — 전체 재동기화 시 무한 누적 방지 */
    fun retainKeys(activeKeys: Set<String>) {
        pendingIntents.keys.retainAll(activeKeys)
    }

    fun clear() {
        pendingIntents.clear()
    }
}
