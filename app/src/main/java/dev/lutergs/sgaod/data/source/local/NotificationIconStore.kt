package dev.lutergs.sgaod.data.source.local

import android.graphics.drawable.Icon
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 알림 아이콘을 관리하는 Data Layer Store
 * Domain Layer의 순수성을 위해 Android 타입인 Icon을 이 클래스에서 관리
 *
 * NotificationListenerService 콜백 스레드와 UI 스레드에서 동시 접근되므로 ConcurrentHashMap 사용
 */
@Singleton
class NotificationIconStore @Inject constructor() {

    private val icons = ConcurrentHashMap<String, Icon>()

    fun register(key: String, icon: Icon) {
        // 같은 리소스를 가리키는 아이콘이면 기존 인스턴스를 유지한다.
        // 재게시마다 IPC 로 새 Icon 인스턴스가 오는데, 인스턴스가 바뀌면
        // UI 의 remember(icon) 캐시가 무효화되어 loadDrawable 이 반복 실행된다.
        val existing = icons[key]
        if (existing != null && isSameResourceIcon(existing, icon)) return
        icons[key] = icon
    }

    fun get(key: String): Icon? = icons[key]

    fun remove(key: String) {
        icons.remove(key)
    }

    /** 활성 알림에 없는 key 를 정리 — 전체 재동기화 시 무한 누적 방지 */
    fun retainKeys(activeKeys: Set<String>) {
        icons.keys.retainAll(activeKeys)
    }

    fun clear() {
        icons.clear()
    }

    private fun isSameResourceIcon(a: Icon, b: Icon): Boolean {
        return try {
            a.type == Icon.TYPE_RESOURCE && b.type == Icon.TYPE_RESOURCE &&
                a.resId == b.resId && a.resPackage == b.resPackage
        } catch (e: Exception) {
            false
        }
    }
}
