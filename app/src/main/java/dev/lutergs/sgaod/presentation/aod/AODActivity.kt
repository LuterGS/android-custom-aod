package dev.lutergs.sgaod.presentation.aod

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.OutcomeReceiver
import android.os.PowerManager
import android.os.SystemClock
import android.view.*
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.data.SystemBrightnessMonitor
import dev.lutergs.sgaod.domain.AodBrightness
import dev.lutergs.sgaod.domain.AodTouch
import dev.lutergs.sgaod.domain.AodTouchGate
import dev.lutergs.sgaod.domain.SleepReason

class AODActivity : Activity() {
    private lateinit var surface: AodSurface
    private var started = false
    private var lastVisible = false
    private var sessionId = -1L
    private val observer: () -> Unit = { synchronize() }
    private val brightnessMode by lazy { SystemBrightnessMonitor(this) { synchronize() } }
    private val touchGate = AodTouchGate()
    private var gestures: GestureDetector? = null
    private fun newGestureDetector() = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!canInteract()) return true
                aodStore.dismissSession?.invoke()
                finish()
                return true
            }
        })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!aodStore.session) { finish(); return }
        sessionId = aodStore.sessionId
        setShowWhenLocked(true)
        window.setDecorFitsSystemWindows(false)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 1f
            screenBrightness = AodBrightness.windowValue(aodStore.sleepReason == SleepReason.NONE,
                brightnessMode.automatic, aodStore.settings.brightness)
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
        brightnessMode.start()
        aodStore.activityStarted?.invoke()
        synchronize()
    }
    override fun onResume() {
        super.onResume()
        window.decorView.post { hideBars() }
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
        if (!visible) cancelGestures()
        val brightness = AodBrightness.windowValue(visible, brightnessMode.automatic, aodStore.settings.brightness)
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
        brightnessMode.stop()
        cancelGestures()
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
                override fun onResult(result: Void?) {
                    // One UI fullscreen transitions can reset bar visibility after the focus callback.
                    window.decorView.post { hideBars() }
                }
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
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsets.Type.systemBars())
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!canInteract()) { cancelGestures(); return true }
        touchGate.setEnabled(true)
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> AodTouch.DOWN
            MotionEvent.ACTION_UP -> AodTouch.UP
            MotionEvent.ACTION_CANCEL -> AodTouch.CANCEL
            else -> AodTouch.MOVE
        }
        if (touchGate.accept(action)) {
            val detector = gestures ?: newGestureDetector().also { gestures = it }
            detector.onTouchEvent(event)
        }
        return true
    }
    private fun canInteract(): Boolean = started && aodStore.session && sessionId == aodStore.sessionId &&
        aodStore.sleepReason == SleepReason.NONE && getSystemService(PowerManager::class.java).isInteractive

    private fun cancelGestures() {
        touchGate.setEnabled(false)
        gestures?.let {
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            it.onTouchEvent(cancel)
            cancel.recycle()
        }
        gestures = null
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
