package dev.lutergs.sgaod.presentation.aod

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.lutergs.sgaod.data.source.local.AodVisibilityController
import dev.lutergs.sgaod.presentation.theme.CustomAODTheme
import dev.lutergs.sgaod.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AODActivity : ComponentActivity() {

    private val viewModel: AODViewModel by viewModels()

    @Inject
    lateinit var aodVisibilityController: AodVisibilityController

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null

    /**
     * 볼륨 버튼으로 진입하는 완전 블랙 모드.
     * true 이면 콘텐츠를 그리지 않고(순수 검정 = OLED 픽셀 소등) 화면 밝기를 최소화한다.
     */
    private val isBlackMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 가장 먼저 전환 애니메이션 비활성화 (잠금화면 깜빡임 방지)
        disableTransitionAnimations()

        // 2. 잠금화면 위 표시 설정 (manifest 선언과 동일 — 런타임 재확인)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 3. 배경을 검정색으로 즉시 설정 (깜빡임 방지)
        window.setBackgroundDrawableResource(android.R.color.black)

        super.onCreate(savedInstanceState)

        // 4. 화면 유지 (wakelock 불필요 — 공식 권장 방식)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 5. 저전력 표시 설정: 낮은 주사율 선호 + 초기 밝기
        applyLowPowerDisplayHints()
        applyBrightness(AodConstants.AOD_SCREEN_BRIGHTNESS)

        // 6. 시스템 바 숨김
        hideSystemBars()
        window.decorView.post { hideSystemBars() }

        // 7. AOD 표시 상태 관찰 — false 가 되면 스스로 종료.
        //    StateFlow 라 이 액티비티가 늦게 시작돼도 현재 값을 즉시 받아 유실이 없다.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                aodVisibilityController.shouldShowAod.collect { shouldShow ->
                    if (!shouldShow) {
                        finish()
                    }
                }
            }
        }

        registerTelephonyCallback()
        observeAmbientLight()

        setContent {
            CustomAODTheme(darkTheme = true) {
                val blackMode by isBlackMode
                AODScreen(
                    viewModel = viewModel,
                    isBlackMode = blackMode,
                    onExitBlackMode = { setBlackMode(false) },
                    onDoubleTap = { finish() }
                )
            }
        }
    }

    /**
     * 볼륨 키를 가로채 블랙 모드를 토글한다.
     * DOWN/UP 모두 소비해야 시스템 볼륨 변경과 볼륨 패널 표시가 완전히 차단된다.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    setBlackMode(!isBlackMode.value)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setBlackMode(enabled: Boolean) {
        if (isBlackMode.value == enabled) return
        isBlackMode.value = enabled
        if (enabled) {
            applyBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
        } else {
            applyBrightness(currentAmbientBrightness)
        }
    }

    private var currentAmbientBrightness = AodConstants.AOD_SCREEN_BRIGHTNESS

    /**
     * 조도 센서 값에 따라 window 밝기 자체를 조절한다.
     * 콘텐츠 알파만 낮추는 것과 달리 실제 패널 구동 전력이 줄어든다.
     */
    private fun observeAmbientLight() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sensorState
                    .map { AodConstants.luxToScreenBrightness(it.lux) }
                    .distinctUntilChanged()
                    .collect { brightness ->
                        currentAmbientBrightness = brightness
                        if (!isBlackMode.value) {
                            applyBrightness(brightness)
                        }
                    }
            }
        }
    }

    private fun applyBrightness(brightness: Float) {
        try {
            window.attributes = window.attributes.apply {
                screenBrightness = brightness
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply brightness", e)
        }
    }

    private fun applyLowPowerDisplayHints() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // 터치 시 주사율 부스트 비활성화 (AOD 에는 불필요)
                window.setFrameRateBoostOnTouchEnabled(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply low power display hints", e)
        }
    }

    private fun registerTelephonyCallback() {
        if (!PermissionUtils.hasPhoneStatePermission(this)) return
        try {
            telephonyManager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.let { tm ->
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_RINGING) {
                            finish()
                        }
                    }
                }
                telephonyCallback = callback
                tm.registerTelephonyCallback(mainExecutor, callback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for telephony callback", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register telephony callback", e)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun finish() {
        super.finish()
        disableTransitionAnimations()
    }

    /**
     * singleInstance 재사용 경로: onCreate 없이 전면에 올 수 있으므로
     * 블랙 모드/밝기 등 잔존 상태를 초기화한다.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setBlackMode(false)
        applyBrightness(currentAmbientBrightness)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 더블탭 등으로 종료될 때 서비스 상태를 동기적으로 되돌린다
        // (startService 왕복은 방금 중지된 서비스를 부활시키는 부작용이 있어 금지).
        // isFinishing 가드: 시스템 재생성 시에는 상태를 건드리지 않는다
        if (isFinishing) {
            aodVisibilityController.hide()
        }

        telephonyCallback?.let { callback ->
            try {
                telephonyManager?.unregisterTelephonyCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister telephony callback", e)
            }
        }
        telephonyCallback = null
        telephonyManager = null
    }

    private fun disableTransitionAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun hideSystemBars() {
        try {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())

            // attributes 재대입 없이는 WindowManager 에 변경이 전달되지 않음
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide system bars", e)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 상단 영역 터치 차단 (상태바 스와이프로 인한 시스템 UI 노출 방지)
        val blockThreshold = getStatusBarHeight() +
            (AodConstants.TOUCH_BLOCK_OFFSET_DP * resources.displayMetrics.density).toInt()

        if (event.y < blockThreshold) {
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    return true  // 이벤트 소비 - 시스템 제스처 방지
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun getStatusBarHeight(): Int {
        val windowInsets = window.decorView.rootWindowInsets
        return windowInsets?.getInsets(WindowInsets.Type.statusBars())?.top
            ?: (DEFAULT_STATUS_BAR_HEIGHT_DP * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "AODActivity"
        private const val DEFAULT_STATUS_BAR_HEIGHT_DP = 32
    }
}
