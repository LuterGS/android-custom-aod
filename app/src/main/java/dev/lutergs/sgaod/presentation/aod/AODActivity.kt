package dev.lutergs.sgaod.presentation.aod

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import android.view.*
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.domain.SleepReason

class AODActivity : Activity() {
    private lateinit var surface: AodSurface
    private var started = false
    private var lastVisible = false
    private var sessionId = -1L
    private val observer: () -> Unit = { synchronize() }
    private val gestures by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                aodStore.dismissSession?.invoke()
                finish()
                return true
            }
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!aodStore.session) { finish(); return }
        sessionId = aodStore.sessionId
        setShowWhenLocked(true)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 1f
            screenBrightness = aodStore.settings.brightness / 100f
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= 35) window.setFrameRateBoostOnTouchEnabled(false)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        surface = AodSurface(this)
        setContentView(surface)
        hideBars()
    }
    override fun onStart() {
        super.onStart()
        if (!::surface.isInitialized) return
        started = true
        isShowing = true
        aodStore.observers += observer
        aodStore.activityStarted?.invoke()
        synchronize()
    }
    private fun synchronize() {
        if (!started) return
        if (!aodStore.session || sessionId != aodStore.sessionId) { finish(); return }
        val visible = aodStore.sleepReason == SleepReason.NONE
        if (visible && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            aodStore.dismissSession?.invoke()
            finish()
            return
        }
        if (visible != lastVisible) {
            lastVisible = visible
            setTurnScreenOn(visible)
            if (visible) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val brightness = if (visible) aodStore.settings.brightness / 100f else 0f
        if (window.attributes.screenBrightness != brightness) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        surface.showContent(visible)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sessionId = aodStore.sessionId
        synchronize()
    }
    override fun onStop() {
        started = false
        isShowing = false
        aodStore.observers -= observer
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setTurnScreenOn(false)
        lastVisible = false
        if (::surface.isInitialized) surface.showContent(false)
        // Allow proximity events to catch up with the system screen-off lifecycle callback.
        if (sessionId == aodStore.sessionId) aodStore.activityStopped?.invoke()
        super.onStop()
    }
    override fun onDestroy() {
        if (::surface.isInitialized) surface.dispose()
        if (isFinishing && aodStore.session && sessionId == aodStore.sessionId) aodStore.dismissSession?.invoke()
        super.onDestroy()
    }
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (isTopResumedActivity && Build.VERSION.SDK_INT >= 34) {
            requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER, object : OutcomeReceiver<Void, Throwable> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: Throwable) = Unit // Window manager may decline in some modes.
            })
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) { hideBars(); synchronize() }
    }
    private fun hideBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        gestures.onTouchEvent(event)
        return true
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_UP) {
                aodStore.dismissSession?.invoke()
                finish()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    companion object { var isShowing = false; private set }
}
