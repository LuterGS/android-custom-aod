package dev.lutergs.sgaod.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.data.source.local.AodVisibilityController
import dev.lutergs.sgaod.data.source.local.NotificationSyncRequester
import dev.lutergs.sgaod.presentation.aod.AODActivity
import dev.lutergs.sgaod.presentation.main.MainActivity
import dev.lutergs.sgaod.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AODService : Service() {

    @Inject
    lateinit var notificationSyncRequester: NotificationSyncRequester

    @Inject
    lateinit var aodVisibilityController: AodVisibilityController

    private var wakeLock: PowerManager.WakeLock? = null
    private var displayManager: DisplayManager? = null
    private var keyguardManager: KeyguardManager? = null
    private var powerManager: PowerManager? = null
    private var sensorManager: SensorManager? = null
    private var telephonyManager: TelephonyManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastAODStartTime = 0L

    /**
     * AOD 표시 중 사용자가 전원 버튼으로 화면을 끈 경우 true.
     * 이 상태에서는 다음 화면 켜짐까지 AOD 를 다시 띄우지 않아,
     * 전원 버튼을 다시 누르면 시스템 잠금화면이 그대로 표시된다.
     */
    private var suppressAODUntilScreenOn = false

    private var isInCall = false
    private var isProximityNear = false
    private var proximitySensor: Sensor? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var isProximitySensorRegistered = false
    private var isScreenStateReceiverRegistered = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                val display = displayManager?.getDisplay(displayId)
                val state = display?.state ?: return

                when (state) {
                    Display.STATE_ON -> checkAndHideAOD()
                    Display.STATE_OFF,
                    Display.STATE_DOZE,
                    Display.STATE_DOZE_SUSPEND -> handleDisplayOff()
                }
            }
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    /**
     * 디스플레이 꺼짐 처리 — 전원 버튼 감지의 1차 경로.
     *
     * DisplayListener 는 경량 바인더 콜백이라 ACTION_SCREEN_OFF ordered broadcast
     * 보다 수백 ms 먼저 도착한다. 여기서 전원 버튼을 판정해야 잠금화면 재점등까지의
     * 암전 시간이 최소화된다 (브로드캐스트 쪽은 폴백으로 유지).
     */
    private fun handleDisplayOff() {
        val timeSinceAODStart = SystemClock.elapsedRealtime() - lastAODStartTime
        if (aodVisibilityController.isAodVisible &&
            timeSinceAODStart > AOD_STARTUP_GRACE_PERIOD_MS &&
            !suppressAODUntilScreenOn
        ) {
            // AOD 표시 중 디스플레이가 꺼짐 = 사용자가 전원 버튼을 누름
            Log.d(TAG, "Power button while AOD active (display off) - waking to lockscreen")
            suppressAODUntilScreenOn = true
            hideAOD()
            wakeScreenToLockscreen()
            return
        }
        showAOD()
    }

    /**
     * 전원 버튼/잠금해제 감지용 리시버.
     * 전원 버튼 자체는 앱이 가로챌 수 없으므로, 그 결과인 SCREEN_OFF/ON 으로 판단한다.
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // 전원 버튼 감지의 2차(폴백) 경로 — 보통은 DisplayListener 의
                    // handleDisplayOff 가 먼저 처리하고 suppress 플래그로 중복을 막는다.
                    // 주의: showAOD 직후 도착한 SCREEN_OFF 를 '전원 버튼 누름'으로
                    // 오분류하지 않도록 grace period 이내의 이벤트는 무시한다.
                    val timeSinceAODStart = SystemClock.elapsedRealtime() - lastAODStartTime
                    if (aodVisibilityController.isAodVisible &&
                        timeSinceAODStart > AOD_STARTUP_GRACE_PERIOD_MS &&
                        !suppressAODUntilScreenOn
                    ) {
                        Log.d(TAG, "Power button while AOD active (broadcast) - waking to lockscreen")
                        suppressAODUntilScreenOn = true
                        hideAOD()
                        wakeScreenToLockscreen()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    suppressAODUntilScreenOn = false
                }
                Intent.ACTION_USER_PRESENT -> {
                    // 지문/트러스트 에이전트 잠금해제 시 디스플레이 상태 전이가 없어
                    // (FLAG_KEEP_SCREEN_ON 으로 STATE_ON 고정) displayListener 로는
                    // 감지 불가 — USER_PRESENT 로 즉시 AOD 를 내린다
                    if (aodVisibilityController.isAodVisible) {
                        Log.d(TAG, "Keyguard dismissed - hiding AOD")
                        hideAOD()
                    }
                }
            }
        }
    }

    private val proximitySensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val distance = it.values[0]
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isProximityNear = distance < maxRange
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChanged(state)
        }
    }

    private fun handleCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                isInCall = false
                unregisterProximitySensor()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                hideAOD()
                isInCall = true
                registerProximitySensor()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                isInCall = true
                registerProximitySensor()
            }
        }
    }

    private fun registerProximitySensor() {
        if (isProximitySensorRegistered) return
        val sm = sensorManager ?: return
        proximitySensor?.let {
            sm.registerListener(
                proximitySensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            isProximitySensorRegistered = true
        }
    }

    private fun unregisterProximitySensor() {
        if (!isProximitySensorRegistered) return
        sensorManager?.unregisterListener(proximitySensorListener)
        isProximitySensorRegistered = false
        isProximityNear = false
    }

    private fun checkAndHideAOD() {
        // 1. Grace period: AOD 시작 직후 setTurnScreenOn으로 인한 STATE_ON 무시
        if (aodVisibilityController.isAodVisible) {
            val timeSinceAODStart = SystemClock.elapsedRealtime() - lastAODStartTime
            if (timeSinceAODStart < AOD_STARTUP_GRACE_PERIOD_MS) {
                return
            }
        }

        // 2. Grace period 이후: keyguard 해제 시 AOD 종료 (지문 인식 등)
        val isLocked = keyguardManager?.isKeyguardLocked ?: return
        if (!isLocked) {
            hideAOD()
        }
    }

    override fun onCreate() {
        super.onCreate()

        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (keyguardManager == null || powerManager == null || displayManager == null) {
            Log.e(TAG, "Required system services not available, stopping service")
            stopSelf()
            return
        }

        displayManager?.registerDisplayListener(displayListener, handler)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            screenFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isScreenStateReceiverRegistered = true

        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        registerTelephonyCallback()

        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Service started")
    }

    private fun registerTelephonyCallback() {
        val tm = telephonyManager ?: return
        if (!PermissionUtils.hasPhoneStatePermission(this)) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted, skipping telephony callback")
            return
        }

        try {
            val callback = CallStateCallback()
            tm.registerTelephonyCallback(mainExecutor, callback)
            telephonyCallback = callback
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while registering telephony callback", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register telephony callback", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideAOD()
        releaseWakeLock()
        handler.removeCallbacksAndMessages(null)

        displayManager?.unregisterDisplayListener(displayListener)

        if (isScreenStateReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister screen state receiver", e)
            }
            isScreenStateReceiverRegistered = false
        }

        unregisterProximitySensor()
        unregisterTelephonyCallback()

        Log.d(TAG, "Service destroyed")
    }

    private fun unregisterTelephonyCallback() {
        val callback = telephonyCallback ?: return
        try {
            telephonyManager?.unregisterTelephonyCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister telephony callback", e)
        }
        telephonyCallback = null
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun showAOD() {
        if (aodVisibilityController.isAodVisible) return

        // 사용자가 전원 버튼으로 AOD 를 끈 직후에는 재표시하지 않음
        if (suppressAODUntilScreenOn) {
            Log.d(TAG, "Skipping AOD - suppressed until next screen on (power button)")
            return
        }

        // 화면이 interactive 상태(사용 중)이면 AOD 표시 안함
        if (powerManager?.isInteractive == true) {
            return
        }

        if (isInCall && isProximityNear) {
            return
        }

        lastAODStartTime = SystemClock.elapsedRealtime()

        acquireWakeLock()

        // 리스너 unbind 등으로 놓친 알림 이벤트가 있을 수 있으므로
        // AOD 를 띄우기 전에 알림 목록 전체 재동기화를 요청한다
        notificationSyncRequester.requestSync()

        // startActivity 이전에 상태를 먼저 세워, 액티비티가 언제 시작되든
        // 현재 상태(true)를 보고 표시를 유지하도록 한다
        aodVisibilityController.show()

        val aodIntent = Intent(this, AODActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        try {
            startActivity(aodIntent)

            // WakeLock 해제를 지연 (Activity가 완전히 표시된 후)
            handler.postDelayed({ releaseWakeLock() }, WAKELOCK_RELEASE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AODActivity", e)
            aodVisibilityController.hide()
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val pm = powerManager ?: return
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CustomAOD:ScreenOffWakeLock"
        ).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release wakelock", e)
            }
        }
        wakeLock = null
    }

    /**
     * 멱등 — 상태만 false 로 내리면 AODActivity 가 StateFlow 를 관찰해 스스로 종료한다.
     * 액티비티가 아직 생성 중이어도 생성 직후 현재 값(false)을 보고 즉시 종료하므로
     * 명령 유실로 인한 desync 가 발생하지 않는다.
     */
    private fun hideAOD() {
        aodVisibilityController.hide()
    }

    /**
     * 화면을 켜서 시스템 잠금화면을 표시한다.
     *
     * SCREEN_BRIGHT_WAKE_LOCK 류는 deprecated 지만, 앱이 화면을 깨울 수 있는
     * 유일한 공개 경로다 (PowerManager.wakeUp 은 시스템 전용). AOD 액티비티가
     * 먼저 종료되도록 짧게 지연한 뒤 깨워, 재점등 시 keyguard 가 최상단에 온다.
     */
    private fun wakeScreenToLockscreen() {
        handler.postDelayed({
            try {
                @Suppress("DEPRECATION")
                val wakeUpLock = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE,
                    "CustomAOD:WakeToLockscreen"
                )
                wakeUpLock?.acquire(WAKE_TO_LOCKSCREEN_HOLD_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to wake screen to lockscreen", e)
            }
        }, WAKE_TO_LOCKSCREEN_DELAY_MS)
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.aod_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.aod_service_notification_title))
            .setContentText(getString(R.string.aod_service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AODService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "aod_service_channel"
        private const val WAKELOCK_TIMEOUT_MS = 5_000L
        private const val WAKELOCK_RELEASE_DELAY_MS = 500L
        private const val AOD_STARTUP_GRACE_PERIOD_MS = 2_000L
        private const val WAKE_TO_LOCKSCREEN_DELAY_MS = 100L
        private const val WAKE_TO_LOCKSCREEN_HOLD_MS = 1_000L
    }
}
