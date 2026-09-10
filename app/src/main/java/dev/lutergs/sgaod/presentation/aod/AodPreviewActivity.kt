package dev.lutergs.sgaod.presentation.aod

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.aodStore
import android.os.SystemClock
import dev.lutergs.sgaod.domain.*

/** Uses only example data and the production renderer; no AOD session or permissions required. */
class AodPreviewActivity : Activity() {
    private var details = false
    private var music = true
    private var priority = false
    private var charging = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val renderer = AodRenderer(this)
        val icons = mapOf(
            "preview-message" to Icon.createWithResource(this, android.R.drawable.ic_dialog_info),
            "preview-calendar" to Icon.createWithResource(this, android.R.drawable.ic_menu_today),
            "preview-mail" to Icon.createWithResource(this, android.R.drawable.ic_dialog_email),
        )
        details = savedInstanceState?.getBoolean("details") ?: false
        music = savedInstanceState?.getBoolean("music") ?: true
        priority = savedInstanceState?.getBoolean("priority") ?: aodStore.settings.priorityPackages.isNotEmpty()
        charging = savedInstanceState?.getInt("charging") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val preview = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                renderer.draw(canvas, width, height, resources.displayMetrics.density, System.currentTimeMillis(),
                    examples(details, music), icons, shift = false,
                    settings = aodStore.settings.copy(priorityPackages = if (priority) setOf("preview.messages") else emptySet()))
            }
            override fun onDetachedFromWindow() { renderer.clear(); super.onDetachedFromWindow() }
        }
        root.addView(Button(this).apply {
            setText(R.string.preview_close); isAllCaps = false
            setTextColor(Color.LTGRAY); setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            setText(R.string.preview_note); setTextColor(Color.GRAY); gravity = android.view.Gravity.CENTER
            setPadding(16, 8, 16, 8)
        })
        fun option(title: Int, checked: Boolean, changed: (Boolean) -> Unit) {
            root.addView(Switch(this).apply {
                setText(title); setTextColor(Color.LTGRAY); isChecked = checked
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setPadding(24, 0, 24, 0)
                setOnCheckedChangeListener { _, value -> changed(value); preview.invalidate() }
            })
        }
        option(R.string.preview_details, details) { details = it }
        option(R.string.preview_music, music) { music = it }
        option(R.string.preview_priority, priority) { priority = it }
        root.addView(Button(this).apply {
            setText(R.string.preview_charging); isAllCaps = false
            setTextColor(aodStore.settings.themeColor)
            setOnClickListener { charging = (charging + 1) % 5; preview.invalidate() }
        })
        root.setOnApplyWindowInsetsListener { view, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("details", details)
        outState.putBoolean("music", music)
        outState.putBoolean("priority", priority)
        outState.putInt("charging", charging)
        super.onSaveInstanceState(outState)
    }

    private fun examples(details: Boolean, music: Boolean): AodContent = AodContent(
        battery = when (charging) {
            1, 2 -> BatteryState(82, 1, charging = true, fast = charging == 2,
                remainingMillis = 18 * 60_000, sampledAtElapsed = SystemClock.elapsedRealtime())
            3 -> BatteryState(82, 1, charging = false)
            4 -> BatteryState(100, 1, full = true)
            else -> BatteryState(82)
        },
        music = if (music) MusicState(getString(R.string.preview_track), getString(R.string.preview_artist), true) else MusicState(),
        notifications = listOf(
            NotificationEntry("preview-message", "preview.messages", getString(R.string.preview_messages),
                if (details) getString(R.string.preview_sender) else "", if (details) getString(R.string.preview_message) else "", System.currentTimeMillis() - 2 * 60_000),
            NotificationEntry("preview-calendar", "preview.calendar", getString(R.string.preview_calendar),
                if (details) getString(R.string.preview_event) else "", if (details) getString(R.string.preview_event_detail) else "", System.currentTimeMillis() - 15 * 60_000),
            NotificationEntry("preview-mail", "preview.mail", getString(R.string.preview_mail),
                if (details) getString(R.string.preview_mail_title) else "", if (details) getString(R.string.preview_mail_body) else "", System.currentTimeMillis() - 42 * 60_000),
        ),
    )
}
