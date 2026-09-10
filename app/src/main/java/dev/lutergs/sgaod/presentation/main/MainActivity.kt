package dev.lutergs.sgaod.presentation.main

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
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
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.service.AODService

class MainActivity : Activity() {
    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private lateinit var enabledSwitch: Switch
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
            requestPermissions(permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray(), 1)
        }
        label(getString(R.string.energy_title), 22f)
        toggle(R.string.pocket, aodStore.settings.pocketDetection) { aodStore.updateSettings(aodStore.settings.copy(pocketDetection = it)) }
        toggle(R.string.face_down, aodStore.settings.faceDownDetection) { aodStore.updateSettings(aodStore.settings.copy(faceDownDetection = it)) }
        toggle(R.string.power_saver, aodStore.settings.respectPowerSaver) { aodStore.updateSettings(aodStore.settings.copy(respectPowerSaver = it)) }
        toggle(R.string.night, aodStore.settings.sleepAtNight) { aodStore.updateSettings(aodStore.settings.copy(sleepAtNight = it)) }
        val brightnessLabel = label(getString(R.string.brightness, aodStore.settings.brightness), 16f)
        column.addView(SeekBar(this).apply {
            min = 1; max = 10; progress = aodStore.settings.brightness
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    brightnessLabel.text = getString(R.string.brightness, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    aodStore.updateSettings(aodStore.settings.copy(brightness = seekBar.progress))
                }
            })
        })
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
        label(getString(R.string.privacy_note), 13f)
        button(R.string.excluded_apps) { chooseExcludedApps() }
        label(getString(R.string.display_title), 22f)
        label(getString(R.string.refresh_explanation), 14f)
        button(R.string.display_diagnostics) { showDiagnostics() }
        label(getString(R.string.usage_note), 14f)
        label(getString(R.string.migration_note), 13f)
    }
    override fun onResume() {
        super.onResume()
        aodStore.settingsScreenVisible = true
        aodStore.observers += observer
        aodStore.refreshNotifications?.invoke()
        refreshStatus()
    }
    override fun onPause() {
        aodStore.observers -= observer
        aodStore.settingsScreenVisible = false
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
    private fun refreshStatus() {
        if (!::status.isInitialized) return
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
    private fun chooseExcludedApps() {
        val names = aodStore.content.notifications.associate { it.packageName to it.appName }.toMutableMap()
        aodStore.settings.excludedPackages.forEach { names.putIfAbsent(it, it) }
        if (names.isEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.no_apps).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val packages = names.keys.sortedBy { names[it] }
        val chosen = aodStore.settings.excludedPackages.toMutableSet()
        AlertDialog.Builder(this).setTitle(R.string.excluded_apps)
            .setMultiChoiceItems(packages.map { names[it]!! }.toTypedArray(), BooleanArray(packages.size) { packages[it] in chosen }) { _, which, checked ->
                if (checked) chosen += packages[which] else chosen -= packages[which]
            }.setPositiveButton(android.R.string.ok) { _, _ ->
                aodStore.updateSettings(aodStore.settings.copy(excludedPackages = chosen.toSet()))
            }.setNegativeButton(android.R.string.cancel, null).show()
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
