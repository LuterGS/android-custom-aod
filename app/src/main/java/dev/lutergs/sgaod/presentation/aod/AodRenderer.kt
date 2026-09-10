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
import dev.lutergs.sgaod.domain.*
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date

/** Shared by the real Surface and the preview. All geometry uses a compact 320-unit column. */
class AodRenderer(private val context: Context) {
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create(regular, 500, false)
    private val clock = Typeface.create(regular, 300, false)
    private val icons = LruCache<String, Pair<Icon, Drawable>>(24)
    private var primary = Color.LTGRAY
    private var secondary = Color.GRAY
    private var subdued = Color.DKGRAY
    private var accent = Color.WHITE
    private var hairline = Color.DKGRAY

    fun draw(canvas: Canvas, width: Int, height: Int, density: Float, now: Long,
        content: AodContent, notificationIcons: Map<String, Icon> = emptyMap(), shift: Boolean = true,
        settings: AodSettings = AodSettings(), elapsed: Long = SystemClock.elapsedRealtime()) {
        canvas.drawColor(Color.BLACK)
        if (width <= 0 || height <= 0) return
        val options = settings.normalized()
        accent = options.themeColor
        primary = blend(accent, Color.WHITE, 0.65f)
        secondary = blend(accent, Color.BLACK, 0.20f)
        subdued = blend(accent, Color.BLACK, 0.40f)
        hairline = blend(accent, Color.BLACK, 0.76f)
        val sections = NotificationLayout.sections(content.notifications, options)
        val music = content.music.title.isNotBlank()
        val locale = context.resources.configuration.locales[0]
        val date = Date(now)
        val dateText = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMEd"), locale).format(date)
        val is24Hour = DateFormat.is24HourFormat(context)
        val time = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale).format(date)
        val dateSize = 17f * options.dateScale / 100f
        val clockSize = 104f * options.clockScale / 100f
        val periodSize = 14f * options.periodScale / 100f
        val dateBaseline = dateSize
        val clockBaseline = dateBaseline + dateSize * 0.3f + 12f + clockSize * 0.8f
        val clockBottom = clockBaseline + clockSize * 0.22f
        val periodBaseline = clockBottom + periodSize
        val batteryBaseline = (if (is24Hour) clockBottom else periodBaseline + periodSize * 0.25f) + 20f
        val musicTop = batteryBaseline + 34f
        val listTop = batteryBaseline + 32f + if (music) 82f else 0f
        val sectionHeadings = options.priorityPackages.isNotEmpty()
        fun sectionHeight(section: NotificationSection) =
            (if (sectionHeadings) 24f else 0f) + section.rows.size * (if (section.detailed) 70f else 39f) +
                (if (section.remaining > 0) 24f else 0f) + 16f
        val designHeight = if (sections.isEmpty()) {
            if (music) musicTop + 55f else batteryBaseline + 8f
        } else listTop + sections.sumOf { sectionHeight(it).toDouble() }.toFloat()
        text.typeface = clock; text.textSize = clockSize; text.fontFeatureSettings = "tnum"
        val designWidth = maxOf(320f, text.measureText(time) + 20f)
        val top = height * 0.16f
        // Requested sizes are preserved until the complete layout reaches the available screen bounds.
        val unit = minOf(density * 1.12f * options.layoutScale / 100f,
            width * 0.90f / designWidth, (height - top - height * 0.06f) / designHeight)
        val offset = BurnInProtection.offsetAt(now)
        canvas.save()
        canvas.translate(width / 2f + (if (shift) offset.x else 0), top + (if (shift) offset.y else 0))
        canvas.scale(unit, unit)
        canvas.translate(-160f, 0f)
        label(canvas, dateText, 160f, dateBaseline, dateSize, secondary, designWidth,
            Paint.Align.CENTER, medium)
        label(canvas, time, 160f, clockBaseline, clockSize, primary, designWidth, Paint.Align.CENTER, clock)
        if (!is24Hour) label(canvas, SimpleDateFormat("a", locale).format(date), 160f, periodBaseline,
            periodSize, secondary, designWidth, Paint.Align.CENTER)
        drawBattery(canvas, content.battery, batteryBaseline, elapsed)
        if (music) drawMusic(canvas, content, musicTop)
        var y = listTop
        sections.forEach { section ->
            if (sectionHeadings) {
                label(canvas, context.getString(if (section.priority) R.string.priority_section else R.string.regular_section),
                    9f, y + 12f, 11f, if (section.priority) accent else subdued, face = medium)
                y += 24f
            }
            section.rows.forEach { row ->
                val entry = row.entry
                notificationIcon(canvas, entry, notificationIcons[entry.key], 9f, y + 1f)
                if (section.detailed) {
                    label(canvas, entry.appName, 42f, y + 13f, 12f, if (section.priority) accent else secondary, 269f, face = medium)
                    val title = entry.title.ifBlank { entry.text }
                    if (title.isNotBlank()) label(canvas, title, 42f, y + 35f, 15f, primary, 269f, face = medium)
                    if (entry.title.isNotBlank() && entry.text.isNotBlank()) {
                        label(canvas, entry.text, 42f, y + 53f, 12f, subdued, 269f)
                    }
                } else {
                    label(canvas, entry.appName, 42f, y + 17f, 14f, if (section.priority) accent else secondary,
                        if (row.count > 1) 231f else 269f)
                    if (row.count > 1) label(canvas, row.count.toString(), 308f, y + 17f, 11f, subdued, 30f, Paint.Align.RIGHT)
                }
                y += if (section.detailed) 70f else 39f
            }
            if (section.remaining > 0) {
                label(canvas, context.getString(R.string.more_notifications, section.remaining),
                    160f, y + 12f, 11f, subdued, align = Paint.Align.CENTER)
                y += 24f
            }
            y += 16f
        }
        canvas.restore()
        icons.snapshot().keys.filter { key -> content.notifications.none { it.key == key } }.forEach(icons::remove)
    }

    private fun drawBattery(canvas: Canvas, battery: BatteryState, baseline: Float, elapsed: Long) {
        val minutes = ChargingPresentation.remainingMinutes(battery, elapsed)
        val status = when {
            battery.full -> context.getString(R.string.battery_full)
            minutes != null -> context.getString(R.string.battery_remaining, minutes)
            battery.charging -> context.getString(R.string.battery_estimating)
            battery.plugged != 0 -> context.getString(R.string.battery_paused)
            else -> ""
        }
        val value = if (battery.percent >= 0) "${battery.percent}%" else "—"
        val caption = value + if (status.isNotEmpty()) "  ·  $status" else ""
        text.typeface = medium; text.textSize = 12f
        val captionWidth = minOf(267f, text.measureText(caption))
        val left = (320f - (42f + captionWidth)) / 2f
        val color = if (battery.charging || (battery.full && battery.plugged != 0)) accent else Color.WHITE
        stroke(color, 1.3f)
        canvas.drawRoundRect(left, baseline - 12f, left + 30f, baseline + 2f, 3f, 3f, ink)
        ink.style = Paint.Style.FILL
        canvas.drawRoundRect(left + 32f, baseline - 8f, left + 34f, baseline - 2f, 0.8f, 0.8f, ink)
        val bolts = ChargingPresentation.bolts(battery)
        if (bolts > 0) {
            repeat(bolts) { index ->
                val x = left + if (bolts == 1) 11f else 5.5f + index * 12f
                val y = baseline - 10f
                val bolt = Path().apply {
                    moveTo(x + 5f, y); lineTo(x, y + 6f); lineTo(x + 3.8f, y + 6f)
                    lineTo(x + 2.3f, y + 11f); lineTo(x + 9f, y + 4.5f)
                    lineTo(x + 5f, y + 4.5f); close()
                }
                canvas.drawPath(bolt, ink)
            }
        } else if (battery.percent > 0) {
            canvas.drawRoundRect(left + 3f, baseline - 9f,
                left + 3f + 24f * battery.percent.coerceIn(0, 100) / 100f, baseline - 1f, 1f, 1f, ink)
        }
        label(canvas, caption, left + 42f, baseline, 12f, color, 267f, face = medium)
    }

    private fun blend(from: Int, to: Int, ratio: Float): Int = Color.rgb(
        (Color.red(from) * (1 - ratio) + Color.red(to) * ratio).toInt(),
        (Color.green(from) * (1 - ratio) + Color.green(to) * ratio).toInt(),
        (Color.blue(from) * (1 - ratio) + Color.blue(to) * ratio).toInt())

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
            drawable.colorFilter = PorterDuffColorFilter(secondary, PorterDuff.Mode.SRC_IN)
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
