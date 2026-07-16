package dev.lutergs.sgaod.data.source.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AOD 표시 여부의 단일 상태 소스 (프로세스 내 공유).
 *
 * 브로드캐스트 기반 종료 통지는 액티비티가 리시버를 등록하기 전 창에서
 * 명령이 유실되어 영구 desync 를 만들 수 있다. StateFlow 는 늦게 구독해도
 * 현재 값을 즉시 받으므로 유실이 원천적으로 불가능하다.
 *
 * - AODService: showAOD/hideAOD 시 상태를 갱신하고, 판단 시 현재 값을 읽는다
 * - AODActivity: 상태를 collect 하여 false 가 되면 스스로 finish 하고,
 *   자신이 종료될 때(더블탭 등) hide() 로 상태를 되돌린다
 */
@Singleton
class AodVisibilityController @Inject constructor() {

    private val _shouldShowAod = MutableStateFlow(false)
    val shouldShowAod: StateFlow<Boolean> = _shouldShowAod.asStateFlow()

    val isAodVisible: Boolean
        get() = _shouldShowAod.value

    fun show() {
        _shouldShowAod.value = true
    }

    fun hide() {
        _shouldShowAod.value = false
    }
}
