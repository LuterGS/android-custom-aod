package dev.lutergs.sgaod.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Display
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.data.EnvironmentSensors
import dev.lutergs.sgaod.data.MediaMonitor
import dev.lutergs.sgaod.domain.*
import dev.lutergs.sgaod.domain.Environment
import dev.lutergs.sgaod.presentation.aod.AODActivity
import dev.lutergs.sgaod.presentation.main.MainActivity
import java.time.ZonedDateTime
import java.io.FileDescriptor
import java.io.PrintWriter

/** Owns one screen-off session. Display changes caused by refresh-rate switching are deliberately ignored. */
class AODService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val alarms by lazy { getSystemService(AlarmManager::class.java) }
    private lateinit var sensors: EnvironmentSensors
    private lateinit var media: MediaMonitor
    private val batteryReader by lazy { dev.lutergs.sgaod.data.BatteryReader(this) }
    private var environment = Environment()
    private var lastSettings = AodSettings()
    private var session = false
    private var awaitingSensors = false
    private var launching = false
    private var launchTime = 0L
    private var proximityLock: PowerManager.WakeLock? = null
    private var receiverRegistered = false
    private var phoneCallback: TelephonyCallback? = null
    private val observer: () -> Unit = { settingsChanged() }
    private val idleTimeout = Runnable { environment = environment.copy(idle = true); evaluate() }
    private val launchTimeout = Runnable {
        if (launching && !AODActivity.isShowing) {
            aodStore.error = getString(R.string.launch_failed)
            endSession()
        }
        launching = false
    }
    private val begin = Runnable { awaitingSensors = false; evaluate() }
    private val confirmScreenOff = Runnable {
        if (session && aodStore.sleepReason == SleepReason.NONE && !power.isInteractive) endSession()
    }
    private val confirmActivityStopped = Runnable {
        if (session && aodStore.sleepReason == SleepReason.NONE && !AODActivity.isShowing) endSession()
    }
    private val nightBoundary = AlarmManager.OnAlarmListener { evaluate(); scheduleNightBoundary() }
    private val thermalListener = PowerManager.OnThermalStatusChangedListener {
        environment = environment.copy(hot = it >= PowerManager.THERMAL_STATUS_SEVERE)
        evaluate()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!session) beginSession()
                    else if (aodStore.sleepReason == SleepReason.NONE &&
                        SystemClock.elapsedRealtime() - launchTime > 1_500) {
                        // A user power press ends this session. Never wake straight back up.
                        handler.removeCallbacks(confirmScreenOff)
                        handler.postDelayed(confirmScreenOff, 400)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (session && aodStore.sleepReason != SleepReason.NONE) {
                        // Cover removal can restore AOD; other explicit wakes return to the lock screen.
                        if (aodStore.sleepReason == SleepReason.COVERED) evaluate() else endSession()
                    }
                }
                Intent.ACTION_USER_PRESENT -> endSession()
                Intent.ACTION_BATTERY_CHANGED -> {
                    val battery = batteryReader.read(intent)
                    environment = environment.copy(batteryPercent = battery.percent, charging = battery.plugged != 0)
                    aodStore.updateContent(aodStore.content.copy(battery = battery))
                    evaluate()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> evaluate()
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                    evaluate(); scheduleNightBoundary(); aodStore.notifyChanged()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, serviceNotification())
        aodStore.serviceRunning = true
        aodStore.error = null
        lastSettings = aodStore.settings
        sensors = EnvironmentSensors(this, handler) { near, down ->
            val wasHidden = environment.covered || environment.faceDown
            environment = environment.copy(covered = near, faceDown = down,
                idle = if (wasHidden && !near && !down) false else environment.idle)
            if (wasHidden && !near && !down) scheduleIdle()
            if (awaitingSensors && sensors.hasInitialState) {
                awaitingSensors = false
                handler.removeCallbacks(begin)
            }
            evaluate()
        }
        media = MediaMonitor(this, handler) { music ->
            aodStore.updateContent(aodStore.content.copy(music = music))
        }
        aodStore.dismissSession = { endSession() }
        aodStore.activityStarted = {
            launching = false
            handler.removeCallbacks(launchTimeout)
            handler.removeCallbacks(confirmActivityStopped)
            evaluate()
            aodStore.refreshNotifications?.invoke()
        }
        aodStore.activityStopped = {
            media.stop()
            handler.removeCallbacks(confirmActivityStopped)
            handler.postDelayed(confirmActivityStopped, 400)
        }
        aodStore.observers += observer
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        receiverRegistered = true
        power.addThermalStatusListener(mainExecutor, thermalListener)
        registerPhoneCallback()
        scheduleNightBoundary()
        aodStore.notifyChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) aodStore.updateSettings(aodStore.settings.copy(enabled = false))
        if (!aodStore.settings.enabled || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        registerPhoneCallback()
        if (!power.isInteractive && !session) beginSession()
        return START_STICKY
    }

    private fun beginSession() {
        if (!aodStore.settings.enabled || session || !Settings.canDrawOverlays(this)) return
        session = true
        environment = environment.copy(covered = false, faceDown = false, idle = false)
        startSensors()
        scheduleIdle()
    }

    private fun startSensors() {
        awaitingSensors = true
        handler.removeCallbacks(begin)
        // Proceed as soon as initial samples arrive; retain a bounded fallback for silent sensors.
        handler.postDelayed(begin, SensorTiming.STARTUP_TIMEOUT_MS)
        sensors.start(aodStore.settings.pocketDetection, aodStore.settings.faceDownDetection)
    }

    private fun evaluate() {
        if (!session || awaitingSensors) return
        environment = environment.copy(powerSaver = power.isPowerSaveMode,
            hot = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE,
            hour = ZonedDateTime.now().hour)
        val reason = AodPolicy.sleepReason(aodStore.settings, environment)
        aodStore.setSession(true, reason)
        if (reason == SleepReason.NONE) {
            if (!keyguard.isKeyguardLocked) { endSession(); return }
            if (!AODActivity.isShowing && !launching) launchAod()
            if (!session) return // A rejected launch has already released this session.
            media.start()
            enableProximityLock()
        } else {
            launching = false
            handler.removeCallbacks(launchTimeout) // Hidden startup is intentional, not a launch failure.
            media.stop()
            // Keep an already-held proximity lock until the Activity has dropped KEEP_SCREEN_ON.
            // This special lock turns the panel off when near; it does not keep the CPU awake.
            if (reason != SleepReason.COVERED) releaseProximityLock()
        }
    }

    private fun launchAod() {
        launching = true
        launchTime = SystemClock.elapsedRealtime()
        try {
            startActivity(Intent(this, AODActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle())
            handler.postDelayed(launchTimeout, 3_000)
        } catch (_: RuntimeException) {
            aodStore.error = getString(R.string.launch_failed)
            launching = false
            endSession()
        }
    }

    // This is a session-scoped SCREEN-OFF lock, not a CPU lock. A timeout could illuminate a covered device.
    @SuppressLint("WakelockTimeout")
    private fun enableProximityLock() {
        if (!aodStore.settings.pocketDetection || proximityLock?.isHeld == true ||
            !power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        try {
            proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "SGAOD:proximity")
                .apply { setReferenceCounted(false); acquire() }
        } catch (_: RuntimeException) { proximityLock = null }
    }
    private fun releaseProximityLock() {
        proximityLock?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        proximityLock = null
    }
    private fun endSession() {
        session = false
        awaitingSensors = false
        launching = false
        handler.removeCallbacks(begin)
        handler.removeCallbacks(idleTimeout)
        handler.removeCallbacks(launchTimeout)
        handler.removeCallbacks(confirmScreenOff)
        handler.removeCallbacks(confirmActivityStopped)
        aodStore.setSession(false)
        sensors.stop()
        media.stop()
        releaseProximityLock()
    }
    private fun scheduleIdle() {
        handler.removeCallbacks(idleTimeout)
        if (session && aodStore.settings.idleMinutes > 0) {
            handler.postDelayed(idleTimeout, aodStore.settings.idleMinutes * 60_000L)
        }
    }
    private fun scheduleNightBoundary() {
        alarms.cancel(nightBoundary)
        if (!aodStore.settings.sleepAtNight) return
        val now = ZonedDateTime.now()
        val next = listOf(7, 23).map { hour ->
            now.withHour(hour).withMinute(0).withSecond(0).withNano(0).let {
                if (it.isAfter(now)) it else it.plusDays(1)
            }
        }.minOrNull()!!
        // Non-wakeup, inexact: a sleeping CPU is never woken for the schedule.
        alarms.set(AlarmManager.RTC, next.toInstant().toEpochMilli(), "SGAOD:night", nightBoundary, handler)
    }
    private fun settingsChanged() {
        val settings = aodStore.settings
        if (lastSettings == settings) return
        val old = lastSettings
        lastSettings = settings
        if (!settings.enabled) { endSession(); stopSelf(); return }
        if (session && (old.pocketDetection != settings.pocketDetection || old.faceDownDetection != settings.faceDownDetection)) {
            releaseProximityLock()
            environment = environment.copy(covered = false, faceDown = false)
            startSensors()
        }
        if (old.idleMinutes != settings.idleMinutes) {
            environment = environment.copy(idle = false)
            scheduleIdle()
        }
        scheduleNightBoundary()
        evaluate()
    }
    private fun registerPhoneCallback() {
        if (phoneCallback != null || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                environment = environment.copy(inCall = state != TelephonyManager.CALL_STATE_IDLE)
                if (environment.inCall) endSession() else evaluate()
            }
        }
        try {
            getSystemService(TelephonyManager::class.java).registerTelephonyCallback(mainExecutor, callback)
            phoneCallback = callback
        } catch (_: SecurityException) { /* Optional permission may have been revoked. */ }
    }
    private fun serviceNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("aod_v2", getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AODService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "aod_v2").setSmallIcon(R.drawable.ic_aod)
            .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.service_text))
            .setContentIntent(open).setOngoing(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build()).build()
    }
    override fun onDestroy() {
        aodStore.observers -= observer
        endSession()
        aodStore.dismissSession = null
        aodStore.activityStopped = null
        aodStore.activityStarted = null
        aodStore.serviceRunning = false
        aodStore.notifyChanged()
        if (receiverRegistered) unregisterReceiver(receiver)
        power.removeThermalStatusListener(thermalListener)
        phoneCallback?.let { getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) }
        alarms.cancel(nightBoundary)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.println("enabled=${aodStore.settings.enabled} session=$session sessionId=${aodStore.sessionId}")
        writer.println("sleepReason=${aodStore.sleepReason} activityVisible=${AODActivity.isShowing}")
        writer.println("covered=${environment.covered} faceDown=${environment.faceDown} interactive=${power.isInteractive}")
        writer.println("proximityScreenOffLock=${proximityLock?.isHeld == true}")
        writer.println("requestedHz=1 contentMaxFps=1 submittedFrames=${aodStore.submittedFrames} lastFrameUptimeMs=${aodStore.lastFrameUptime}")
        writer.println("Frame counters include immediate blackouts and Surface recreation, and do not measure panel Hz.")
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_STOP = "dev.lutergs.sgaod.STOP" }
}
