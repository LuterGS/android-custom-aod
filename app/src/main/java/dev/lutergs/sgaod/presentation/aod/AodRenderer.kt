package dev.lutergs.sgaod.presentation.aod

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.LruCache
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.domain.AodContent
import dev.lutergs.sgaod.domain.BurnInProtection
import dev.lutergs.sgaod.domain.NotificationEntry
import java.text.SimpleDateFormat
import java.util.Date

/** Shared by the real Surface and the preview. All geometry uses a compact 320-unit column. */
class AodRenderer(private val context: Context) {
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create(regular, 500, false)
    private val clock = Typeface.create(regular, 300, false)
    private val icons = LruCache<String, Pair<Icon, Drawable>>(12)
    private val primary = Color.rgb(195, 205, 205)
    private val secondary = Color.rgb(130, 144, 145)
    private val subdued = Color.rgb(98, 110, 112)
    private val accent = Color.rgb(148, 180, 165)
    private val hairline = Color.rgb(42, 50, 51)

    fun draw(canvas: Canvas, width: Int, height: Int, density: Float, now: Long,
        content: AodContent, notificationIcons: Map<String, Icon> = emptyMap(), shift: Boolean = true) {
        canvas.drawColor(Color.BLACK)
        if (width <= 0 || height <= 0) return
        val detailed = content.notifications.any { it.title.isNotBlank() || it.text.isNotBlank() }
        val groups = content.notifications.groupBy { it.packageName }.values.toList()
        val rows = if (detailed) content.notifications.take(3) else groups.take(4).map { it.first() }
        val shownCount = if (detailed) rows.size else groups.take(4).sumOf { it.size }
        val remaining = content.notifications.size - shownCount
        val music = content.music.title.isNotBlank()
        val rowHeight = if (detailed) 70f else 39f
        val listTop = 180f + if (music) 82f else 0f
        val designHeight = if (rows.isEmpty()) {
            if (music) 238f else 158f
        } else listTop + rows.size * rowHeight + if (remaining > 0) 24f else 0f
        val top = height * 0.20f
        val unit = minOf(density * 1.12f, width * 0.86f / 320f, (height - top - height * 0.08f) / designHeight)
        val offset = BurnInProtection.offsetAt(now)
        // Translate before scaling: these are physical pixels, not density-scaled units.
        val dx = if (shift) offset.x.toFloat() else 0f
        val dy = if (shift) offset.y.toFloat() else 0f
        canvas.save()
        canvas.translate((width - 320 * unit) / 2f + dx, top + dy)
        canvas.scale(unit, unit)

        val locale = context.resources.configuration.locales[0]
        val date = Date(now)
        val datePattern = DateFormat.getBestDateTimePattern(locale, "MMMEd")
        label(canvas, SimpleDateFormat(datePattern, locale).format(date), 160f, 17f, 15f, secondary,
            align = Paint.Align.CENTER, face = medium)
        val is24Hour = DateFormat.is24HourFormat(context)
        val time = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale).format(date)
        text.typeface = clock
        text.textSize = 104f
        text.fontFeatureSettings = "tnum"
        val clockSize = minOf(104f, 300f / text.measureText(time) * 104f)
        label(canvas, time, 160f, 117f, clockSize, primary, 320f, Paint.Align.CENTER, clock)
        if (!is24Hour) label(canvas, SimpleDateFormat("a", locale).format(date), 160f, 138f,
            10f, subdued, align = Paint.Align.CENTER)
        drawBattery(canvas, content, if (is24Hour) 145f else 156f)

        if (music) drawMusic(canvas, content, 187f)
        rows.forEachIndexed { index, entry ->
            val y = listTop + index * rowHeight
            notificationIcon(canvas, entry, notificationIcons[entry.key], 9f, y + 1f)
            if (detailed) {
                label(canvas, entry.appName, 42f, y + 13f, 12f, secondary, 269f, face = medium)
                val title = entry.title.ifBlank { entry.text }
                if (title.isNotBlank()) label(canvas, title, 42f, y + 35f, 15f, primary, 269f, face = medium)
                if (entry.title.isNotBlank() && entry.text.isNotBlank()) {
                    label(canvas, entry.text, 42f, y + 53f, 12f, subdued, 269f)
                }
            } else {
                val count = groups[index].size
                label(canvas, entry.appName, 42f, y + 17f, 14f, secondary, if (count > 1) 231f else 269f)
                if (count > 1) label(canvas, count.toString(), 308f, y + 17f, 11f, subdued,
                    30f, Paint.Align.RIGHT)
            }
        }
        if (remaining > 0) label(canvas, context.getString(R.string.more_notifications, remaining),
            160f, listTop + rows.size * rowHeight + 12f, 11f, subdued, align = Paint.Align.CENTER)
        canvas.restore()
        // Notification resources must not outlive the notifications that supplied them.
        icons.snapshot().keys.filter { key -> content.notifications.none { it.key == key } }.forEach(icons::remove)
    }

    private fun drawBattery(canvas: Canvas, content: AodContent, baseline: Float) {
        val battery = content.battery
        val status = when {
            battery.full -> context.getString(R.string.battery_full)
            battery.plugged == 4 -> context.getString(R.string.battery_wireless)
            battery.plugged != 0 -> context.getString(R.string.battery_charging)
            else -> ""
        }
        val value = if (battery.percent >= 0) "${battery.percent}%" else "—"
        val caption = value + if (status.isNotEmpty()) "  ·  $status" else ""
        text.typeface = medium; text.textSize = 12f
        val left = (320f - (31f + text.measureText(caption))) / 2f
        val color = if (battery.plugged != 0) accent else secondary
        stroke(color, 1.2f)
        canvas.drawRoundRect(left, baseline - 9f, left + 21f, baseline + 1f, 2.5f, 2.5f, ink)
        ink.style = Paint.Style.FILL
        canvas.drawRoundRect(left + 23f, baseline - 6f, left + 24.5f, baseline - 2f, 0.7f, 0.7f, ink)
        if (battery.percent > 0) {
            canvas.drawRoundRect(left + 2.5f, baseline - 6.5f,
                left + 2.5f + 16f * battery.percent.coerceIn(0, 100) / 100f, baseline - 1.5f, 1f, 1f, ink)
        }
        label(canvas, caption, left + 31f, baseline, 12f, color, 260f, face = medium)
    }

    private fun drawMusic(canvas: Canvas, content: AodContent, top: Float) {
        stroke(hairline, 0.8f)
        canvas.drawLine(9f, top - 10f, 311f, top - 10f, ink)
        val playing = content.music.playing
        val color = if (playing) accent else subdued
        ink.color = color; ink.style = Paint.Style.FILL
        if (playing) {
            // A static waveform indicates playback without animation or a progress timer.
            floatArrayOf(7f, 17f, 12f, 21f).forEachIndexed { index, size ->
                val x = 11f + index * 5f
                canvas.drawRoundRect(x, top + 21f - size / 2, x + 2.5f, top + 21f + size / 2, 1.2f, 1.2f, ink)
            }
        } else {
            canvas.drawRoundRect(15f, top + 13f, 18f, top + 28f, 1f, 1f, ink)
            canvas.drawRoundRect(23f, top + 13f, 26f, top + 28f, 1f, 1f, ink)
        }
        label(canvas, context.getString(if (playing) R.string.music_playing else R.string.music_paused),
            47f, top + 4f, 10f, color, 264f, face = medium)
        label(canvas, content.music.title, 47f, top + 25f, 16f, primary, 264f, face = medium)
        if (content.music.artist.isNotBlank()) label(canvas, content.music.artist, 47f, top + 44f,
            12f, secondary, 264f)
    }

    private fun notificationIcon(canvas: Canvas, entry: NotificationEntry, icon: Icon?, x: Float, y: Float) {
        val cached = icons.get(entry.key)
        val drawable = if (icon == null) null else if (cached?.first === icon) cached.second else {
            runCatching { icon.loadDrawable(context)?.mutate() }.getOrNull()?.also {
                it.colorFilter = PorterDuffColorFilter(secondary, PorterDuff.Mode.SRC_IN)
                icons.put(entry.key, icon to it)
            }
        }
        if (drawable != null) {
            drawable.setBounds(x.toInt(), y.toInt(), (x + 21f).toInt(), (y + 21f).toInt())
            runCatching { drawable.draw(canvas) }.onSuccess { return }
        }
        // A neutral outline bell also works for missing icons and work-profile resources.
        stroke(secondary, 1.25f)
        val bell = Path().apply {
            moveTo(x + 3f, y + 15f); lineTo(x + 5f, y + 12f); lineTo(x + 5f, y + 8f)
            cubicTo(x + 5f, y + 1f, x + 16f, y + 1f, x + 16f, y + 8f)
            lineTo(x + 16f, y + 12f); lineTo(x + 18f, y + 15f); close()
        }
        canvas.drawPath(bell, ink)
        canvas.drawArc(x + 8f, y + 16f, x + 13f, y + 20f, 0f, 180f, false, ink)
    }

    private fun label(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float,
        color: Int, maxWidth: Float = 300f, align: Paint.Align = Paint.Align.LEFT, face: Typeface = regular) {
        text.typeface = face; text.textSize = size; text.color = color; text.textAlign = align
        text.fontFeatureSettings = "tnum"
        val clean = value.replace('\n', ' ').replace('\r', ' ')
        canvas.drawText(TextUtils.ellipsize(clean, text, maxWidth, TextUtils.TruncateAt.END).toString(), x, baseline, text)
    }

    private fun stroke(color: Int, width: Float) {
        ink.color = color; ink.style = Paint.Style.STROKE; ink.strokeWidth = width
        ink.strokeCap = Paint.Cap.ROUND; ink.strokeJoin = Paint.Join.ROUND
    }

    fun clear() { icons.evictAll() }
}
