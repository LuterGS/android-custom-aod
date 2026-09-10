package dev.lutergs.sgaod.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Observe the user's mode; the OS owns ambient-light sampling and its brightness curve. */
class SystemBrightnessMonitor(context: Context, private val changed: () -> Unit) {
    private val resolver = context.contentResolver
    private var observing = false
    var automatic = read()
        private set
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }
    private fun read(): Boolean = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun refresh() {
        val value = read()
        if (automatic != value) { automatic = value; changed() }
    }
    fun start() {
        if (!observing) {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
            observing = true
        }
        refresh()
    }
    fun stop() {
        if (observing) resolver.unregisterContentObserver(observer)
        observing = false
    }
}
