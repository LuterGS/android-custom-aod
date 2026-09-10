package dev.lutergs.sgaod.presentation.aod

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.domain.FrameGate
import dev.lutergs.sgaod.domain.BurnInProtection

/** A static buffer: updates are coalesced to <= 1 fps, no Choreographer/animation loop. */
class AodSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private val gate = FrameGate()
    private val renderer = AodRenderer(context)
    private var ready = false
    private var visibleContent = false
    private var disposed = false
    private val draw = Runnable { render(); scheduleMinute() }
    private val minute = Runnable { requestFrame() }
    var frameCount: Long = 0
        private set
    init {
        holder.addCallback(this)
        // Keep the View transparent: an opaque View background covers the Surface behind it.
        // Black is drawn into the Surface buffer and provided by the Activity window.
        contentDescription = context.getString(R.string.aod_description)
    }
    fun showContent(show: Boolean) {
        if (visibleContent == show) { if (show) requestFrame(); return }
        visibleContent = show
        if (!show) renderer.clear()
        handler.removeCallbacksAndMessages(null)
        // Blackout is immediate for privacy/pocket safety, exempt from the content throttle.
        if (!show && ready) render() else requestFrame()
    }
    fun requestFrame() {
        if (disposed || !ready || !visibleContent || handler.hasCallbacks(draw)) return
        handler.postDelayed(draw, gate.delay(SystemClock.uptimeMillis()))
    }
    private fun scheduleMinute() {
        handler.removeCallbacks(minute)
        if (visibleContent && ready && !disposed) {
            handler.postDelayed(minute, BurnInProtection.untilNextMinute(System.currentTimeMillis()))
        }
    }
    private fun render() {
        if (!ready || disposed || !holder.surface.isValid) return
        val canvas = try { holder.lockCanvas() } catch (_: IllegalArgumentException) { null } ?: return
        try {
            canvas.drawColor(Color.BLACK)
            if (visibleContent) drawContent(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
        frameCount++
        context.aodStore.submittedFrames++
        context.aodStore.lastFrameUptime = SystemClock.uptimeMillis()
        gate.rendered(context.aodStore.lastFrameUptime)
    }
    private fun drawContent(canvas: Canvas) {
        renderer.draw(canvas, width, height, resources.displayMetrics.density, System.currentTimeMillis(),
            context.aodStore.content, context.aodStore.notificationIcons)
    }
    override fun surfaceCreated(holder: SurfaceHolder) {
        ready = true
        try {
            holder.surface.setFrameRate(1f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        } catch (_: IllegalArgumentException) { /* OEM may reject the hint; it is never a guarantee. */ }
        if (visibleContent) requestFrame() else render()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (visibleContent) requestFrame() else if (ready) render()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        ready = false
        handler.removeCallbacksAndMessages(null)
    }
    fun dispose() {
        disposed = true
        renderer.clear()
        handler.removeCallbacksAndMessages(null)
        holder.removeCallback(this)
    }
}
