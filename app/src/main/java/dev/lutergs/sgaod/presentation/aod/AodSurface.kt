package dev.lutergs.sgaod.presentation.aod

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.text.TextPaint
import android.text.format.DateFormat
import android.view.*
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.domain.FrameGate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A static buffer: updates are coalesced to <= 1 fps, no Choreographer/animation loop. */
class AodSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val handler = Handler(Looper.getMainLooper())
    private val gate = FrameGate()
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var ready = false
    private var visibleContent = false
    private var disposed = false
    private val draw = Runnable { render(); scheduleMinute() }
    private val minute = Runnable { requestFrame() }
    var frameCount: Long = 0
        private set
    init {
        holder.addCallback(this)
        setBackgroundColor(Color.BLACK)
        contentDescription = context.getString(R.string.aod_description)
    }
    fun showContent(show: Boolean) {
        if (visibleContent == show) { if (show) requestFrame(); return }
        visibleContent = show
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
            handler.postDelayed(minute, 60_000 - System.currentTimeMillis() % 60_000)
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
        val density = resources.displayMetrics.density
        val scale = minOf(width / (360f * density), height / (800f * density), 1.2f).coerceAtLeast(0.3f)
        val unit = density * scale
        val now = System.currentTimeMillis()
        val minuteIndex = now / 60_000
        // A bounded 7x7 pixel orbit. No persistent motion animation.
        val dx = (minuteIndex % 7 - 3).toFloat()
        val dy = (minuteIndex / 7 % 7 - 3).toFloat()
        canvas.save()
        canvas.translate(dx, dy)
        var y = height * 0.16f
        val available = minOf(width - 60f * unit, 360f * unit)
        val left = (width - available) / 2f
        fun line(text: String, size: Float, center: Boolean = true, dim: Boolean = false) {
            paint.textSize = size * unit
            paint.color = if (dim) Color.rgb(110, 110, 110) else Color.rgb(175, 175, 175)
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
            val clean = text.replace('\n', ' ').replace('\r', ' ')
            val fitted = TextUtils.ellipsize(clean, paint, available, TextUtils.TruncateAt.END).toString()
            canvas.drawText(fitted, if (center) width / 2f else left, y, paint)
            y += (size + 12) * unit
        }
        val time = DateFormat.getTimeFormat(context).format(Date(now))
        line(time, 64f)
        line(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now)), 16f, dim = true)
        val data = context.aodStore.content
        val battery = data.battery
        val charging = when {
            battery.full -> context.getString(R.string.battery_full)
            battery.plugged == 4 -> context.getString(R.string.battery_wireless)
            battery.plugged != 0 -> context.getString(R.string.battery_charging)
            else -> ""
        }
        line((if (battery.percent >= 0) "${battery.percent}%" else "—") + "  " + charging, 15f, dim = true)
        y += 18 * unit
        data.notifications.take(4).forEach { entry ->
            line(entry.appName + if (entry.title.isNotBlank()) " · ${entry.title}" else "", 15f, center = false)
            if (entry.text.isNotBlank()) line(entry.text, 12f, center = false, dim = true)
        }
        if (data.notifications.size > 4) line("+${data.notifications.size - 4}", 12f, dim = true)
        if (data.music.title.isNotBlank()) {
            y += 16 * unit
            line(context.getString(if (data.music.playing) R.string.music_playing else R.string.music_paused), 12f, dim = true)
            line(data.music.title, 17f)
            if (data.music.artist.isNotBlank()) line(data.music.artist, 13f, dim = true)
        }
        canvas.restore()
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
        handler.removeCallbacksAndMessages(null)
        holder.removeCallback(this)
    }
}
