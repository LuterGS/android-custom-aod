package dev.lutergs.sgaod.presentation.main

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.text.InputType
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.domain.NotificationTimeFormat
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.data.AppLabelResolver
import dev.lutergs.sgaod.data.SystemBrightnessMonitor
import dev.lutergs.sgaod.presentation.aod.AodPreviewActivity
import dev.lutergs.sgaod.service.AODService

class MainActivity : Activity() {
    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var brightnessLabel: TextView
    private lateinit var brightnessSlider: SeekBar
    private lateinit var brightnessHint: TextView
    private val brightnessMode by lazy { SystemBrightnessMonitor(this) { refreshBrightnessControls() } }
    private var refreshing = false
    private val observer: () -> Unit = { refreshStatus() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(16, 18, 20)); isFillViewport = true }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(40))
        }
        scroll.addView(column)
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        label(getString(R.string.app_name), 32f)
        label(getString(R.string.tagline), 15f)
        button(R.string.aod_preview) { startActivity(Intent(this, AodPreviewActivity::class.java)) }
        status = label("", 14f)
        enabledSwitch = toggle(R.string.enable_aod, aodStore.settings.enabled) { value ->
            if (!refreshing) {
                if (value && !Settings.canDrawOverlays(this)) {
                    refreshing = true; enabledSwitch.isChecked = false; refreshing = false
                    openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    aodStore.updateSettings(aodStore.settings.copy(enabled = value))
                    if (value) startAod() else stopService(Intent(this, AODService::class.java))
                    refreshStatus()
                }
            }
        }
        button(R.string.overlay_permission) {
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button(R.string.notification_access) { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button(R.string.runtime_permissions) {
            val permissions = mutableListOf(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
            val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
            else Toast.makeText(this, R.string.permissions_already_granted, Toast.LENGTH_SHORT).show()
        }
        label(getString(R.string.energy_title), 22f)
        toggle(R.string.pocket, aodStore.settings.pocketDetection) { aodStore.updateSettings(aodStore.settings.copy(pocketDetection = it)) }
        toggle(R.string.face_down, aodStore.settings.faceDownDetection) { aodStore.updateSettings(aodStore.settings.copy(faceDownDetection = it)) }
        toggle(R.string.power_saver, aodStore.settings.respectPowerSaver) { aodStore.updateSettings(aodStore.settings.copy(respectPowerSaver = it)) }
        toggle(R.string.night, aodStore.settings.sleepAtNight) { aodStore.updateSettings(aodStore.settings.copy(sleepAtNight = it)) }
        brightnessLabel = label(getString(R.string.brightness, aodStore.settings.brightness), 16f)
        brightnessSlider = SeekBar(this).apply {
            min = 1; max = 100; progress = aodStore.settings.brightness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    brightnessLabel.text = getString(R.string.brightness, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    aodStore.updateSettings(aodStore.settings.copy(brightness = seekBar.progress))
                }
            })
        }
        column.addView(brightnessSlider)
        brightnessHint = label("", 13f)
        refreshBrightnessControls()
        button(R.string.idle_timeout) {
            val values = intArrayOf(15, 30, 60, 120, 0)
            AlertDialog.Builder(this).setTitle(R.string.idle_timeout)
                .setSingleChoiceItems(values.map { if (it == 0) getString(R.string.never) else getString(R.string.minutes, it) }.toTypedArray(),
                    values.indexOf(aodStore.settings.idleMinutes)) { dialog, which ->
                    aodStore.updateSettings(aodStore.settings.copy(idleMinutes = values[which])); dialog.dismiss()
                }.show()
        }
        label(getString(R.string.notifications_title), 22f)
        toggle(R.string.show_content, aodStore.settings.showNotificationContent) {
            aodStore.updateSettings(aodStore.settings.copy(showNotificationContent = it))
        }
        button(R.string.notification_time_format) {
            val modes = NotificationTimeFormat.entries
            AlertDialog.Builder(this).setTitle(R.string.notification_time_format)
                .setSingleChoiceItems(resources.getStringArray(R.array.notification_time_formats),
                    modes.indexOf(aodStore.settings.notificationTimeFormat)) { dialog, index ->
                    aodStore.updateSettings(aodStore.settings.copy(notificationTimeFormat = modes[index]))
                    dialog.dismiss()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        label(getString(R.string.privacy_note), 13f)
        button(R.string.priority_apps) { chooseApps(true) }
        label(getString(R.string.priority_note), 13f)
        numberControl(R.string.priority_count, aodStore.settings.maxPriorityNotifications, 0, 5) {
            aodStore.updateSettings(aodStore.settings.copy(maxPriorityNotifications = it))
        }
        numberControl(R.string.notification_count, aodStore.settings.maxNotifications, 0, 10) {
            aodStore.updateSettings(aodStore.settings.copy(maxNotifications = it))
        }
        label(getString(R.string.notification_count_note), 13f)
        button(R.string.excluded_apps) { chooseApps(false) }
        label(getString(R.string.appearance_title), 22f)
        button(R.string.theme_color) { chooseTheme() }
        numberControl(R.string.clock_scale, aodStore.settings.clockScale, 50, 200) {
            aodStore.updateSettings(aodStore.settings.copy(clockScale = it))
        }
        numberControl(R.string.date_scale, aodStore.settings.dateScale, 50, 200) {
            aodStore.updateSettings(aodStore.settings.copy(dateScale = it))
        }
        numberControl(R.string.period_scale, aodStore.settings.periodScale, 50, 200) {
            aodStore.updateSettings(aodStore.settings.copy(periodScale = it))
        }
        numberControl(R.string.layout_scale, aodStore.settings.layoutScale, 50, 200) {
            aodStore.updateSettings(aodStore.settings.copy(layoutScale = it))
        }
        label(getString(R.string.scale_note), 13f)
        button(R.string.aod_preview) { startActivity(Intent(this, AodPreviewActivity::class.java)) }
        label(getString(R.string.charging_note), 13f)
        label(getString(R.string.display_title), 22f)
        label(getString(R.string.refresh_explanation), 14f)
        button(R.string.display_diagnostics) { showDiagnostics() }
        label(getString(R.string.usage_note), 14f)
        label(getString(R.string.migration_note), 13f)
    }
    override fun onResume() {
        super.onResume()
        aodStore.settingsScreenVisible = true
        brightnessMode.start()
        refreshBrightnessControls()
        aodStore.observers += observer
        aodStore.refreshNotifications?.invoke()
        refreshStatus()
    }
    override fun onPause() {
        aodStore.observers -= observer
        aodStore.settingsScreenVisible = false
        brightnessMode.stop()
        super.onPause()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (aodStore.settings.enabled && aodStore.serviceRunning) startAod()
        refreshStatus()
    }
    private fun startAod() {
        try {
            startForegroundService(Intent(this, AODService::class.java))
        } catch (_: RuntimeException) {
            aodStore.error = getString(R.string.launch_failed)
            aodStore.updateSettings(aodStore.settings.copy(enabled = false))
        }
    }
    private fun refreshBrightnessControls() {
        if (!::brightnessSlider.isInitialized) return
        val automatic = brightnessMode.automatic
        brightnessSlider.isEnabled = !automatic
        brightnessSlider.alpha = if (automatic) 0.4f else 1f
        brightnessLabel.text = if (automatic) getString(R.string.brightness_automatic)
            else getString(R.string.brightness, aodStore.settings.brightness)
        brightnessHint.setText(if (automatic) R.string.brightness_auto_note else R.string.brightness_manual_note)
    }
    private fun refreshStatus() {
        if (!::status.isInitialized) return
        tintControls(column)
        refreshing = true
        enabledSwitch.isChecked = aodStore.settings.enabled
        refreshing = false
        status.text = listOf(
            getString(if (aodStore.serviceRunning) R.string.status_running else R.string.status_stopped),
            getString(if (aodStore.listenerConnected) R.string.listener_connected else R.string.listener_missing),
            if (!Settings.canDrawOverlays(this)) getString(R.string.overlay_missing) else "",
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) getString(R.string.phone_missing) else "",
            aodStore.error.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n")
    }
    private fun numberControl(title: Int, initial: Int, minimum: Int, maximum: Int, changed: (Int) -> Unit) {
        val caption = label(getString(title, initial), 16f)
        val slider = SeekBar(this).apply {
            min = minimum; max = maximum; progress = initial
            contentDescription = getString(title, initial)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    caption.text = getString(title, progress)
                    seekBar.contentDescription = caption.text
                    if (fromUser) changed(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        column.addView(slider)
    }

    private fun chooseApps(priority: Boolean) {
        val labels = AppLabelResolver(this)
        val names = aodStore.content.notifications.associate { it.packageName to it.appName }.toMutableMap()
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .forEach { info ->
                val pkg = info.activityInfo.packageName
                if (pkg != packageName) names.putIfAbsent(pkg, labels.resolve(pkg))
            }
        (aodStore.settings.excludedPackages + aodStore.settings.priorityPackages).forEach {
            names.putIfAbsent(it, labels.resolve(it))
        }
        if (names.isEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.no_apps).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val packages = names.keys.sortedBy { names[it] }
        val chosen = (if (priority) aodStore.settings.priorityPackages else aodStore.settings.excludedPackages).toMutableSet()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val search = EditText(this).apply { setHint(R.string.search_apps); setSingleLine(true) }
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        var filtered = packages
        fun populate(query: String) {
            filtered = packages.filter { names[it]!!.contains(query, ignoreCase = true) }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, filtered.map { names[it]!! })
            list.clearChoices()
            filtered.forEachIndexed { index, pkg -> list.setItemChecked(index, pkg in chosen) }
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = filtered[position]
            if (list.isItemChecked(position)) chosen += pkg else chosen -= pkg
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populate(s.toString()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        body.addView(search)
        body.addView(list, LinearLayout.LayoutParams(-1, dp(320)))
        populate("")
        AlertDialog.Builder(this).setTitle(if (priority) R.string.priority_apps else R.string.excluded_apps)
            .setView(body).setPositiveButton(android.R.string.ok) { _, _ ->
                aodStore.updateSettings(if (priority) aodStore.settings.copy(priorityPackages = chosen.toSet())
                    else aodStore.settings.copy(excludedPackages = chosen.toSet()))
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseTheme() {
        val colors = intArrayOf(0xff94b4a5.toInt(), 0xffe6e6e6.toInt(), 0xff8bbcf2.toInt(),
            0xffb9a0df.toInt(), 0xffdf9fad.toInt(), 0xffd9b875.toInt())
        val titles = resources.getStringArray(R.array.theme_presets)
        AlertDialog.Builder(this).setTitle(R.string.theme_color)
            .setSingleChoiceItems(titles, colors.indexOf(aodStore.settings.themeColor)) { dialog, index ->
                if (index < colors.size) aodStore.updateSettings(aodStore.settings.copy(themeColor = colors[index]))
                dialog.dismiss()
                if (index == colors.size) customColor()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun customColor() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val field = EditText(this).apply {
            setSingleLine(true); setHint(R.string.color_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
            setText(String.format("#%06X", aodStore.settings.themeColor and 0xffffff))
        }
        val swatch = View(this).apply { setBackgroundColor(aodStore.settings.themeColor) }
        body.addView(field); body.addView(swatch, LinearLayout.LayoutParams(-1, dp(40)))
        fun parsed(): Int? {
            val hex = field.text.toString().trim().removePrefix("#")
            return if (hex.matches(Regex("[0-9a-fA-F]{6}"))) Color.parseColor("#$hex") else null
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                parsed()?.let { swatch.setBackgroundColor(it) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val dialog = AlertDialog.Builder(this).setTitle(R.string.custom_color).setView(body)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val color = parsed()
                if (color == null) field.error = getString(R.string.color_invalid)
                else { aodStore.updateSettings(aodStore.settings.copy(themeColor = color)); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun tintControls(view: View) {
        val accent = aodStore.settings.themeColor
        val active = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, Color.GRAY))
        when (view) {
            is Switch -> { view.thumbTintList = active; view.trackTintList = active }
            is SeekBar -> {
                view.progressTintList = ColorStateList.valueOf(accent)
                view.thumbTintList = ColorStateList.valueOf(accent)
            }
            is Button -> view.setTextColor(accent)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) tintControls(view.getChildAt(i))
    }

    private fun showDiagnostics() {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val sensors = getSystemService(SensorManager::class.java)
        val modes = display?.supportedModes?.joinToString("\n") { "${it.physicalWidth} × ${it.physicalHeight}: ${it.refreshRate} Hz" }.orEmpty()
        val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val text = getString(R.string.diagnostics_body, Build.MODEL, Build.VERSION.RELEASE,
            display?.refreshRate ?: 0f, modes, proximity?.name ?: "—", gravity?.name ?: "—",
            getSystemService(PowerManager::class.java).isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK).toString())
        AlertDialog.Builder(this).setTitle(R.string.display_diagnostics).setMessage(text)
            .setPositiveButton(android.R.string.ok, null).show()
    }
    private fun openSettings(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
    private fun label(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(210, 216, 220))
        setPadding(0, dp(12), 0, dp(12))
        column.addView(this)
    }
    private fun button(text: Int, action: () -> Unit) {
        column.addView(Button(this).apply { setText(text); isAllCaps = false; setOnClickListener { action() } })
    }
    private fun toggle(text: Int, checked: Boolean, changed: (Boolean) -> Unit): Switch = Switch(this).apply {
        setText(text); textSize = 16f; setTextColor(Color.WHITE); isChecked = checked
        minHeight = dp(56); setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, value -> changed(value) }
        column.addView(this)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
