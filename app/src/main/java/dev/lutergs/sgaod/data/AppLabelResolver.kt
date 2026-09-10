package dev.lutergs.sgaod.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.service.notification.StatusBarNotification
import dev.lutergs.sgaod.R
import dev.lutergs.sgaod.domain.AppLabels

/** Resolve on notification changes, never from the drawing loop. Failures are not cached. */
class AppLabelResolver(private val context: Context) {
    private val packages = context.packageManager

    fun resolve(packageName: String, notification: StatusBarNotification? = null): String {
        val installed = runCatching { packages.getApplicationInfo(packageName, 0) }.getOrNull()
            ?.takeIf { notification == null || it.uid == notification.uid }
        label(installed, packageName)?.let { return it }

        // AOSP includes the posting ApplicationInfo in notification extras. This is optional
        // metadata, not an SDK guarantee; validate the owner and keep the public lookup above.
        val attached = runCatching {
            val extras = notification?.notification?.extras
            if (Build.VERSION.SDK_INT >= 33) extras?.getParcelable("android.appInfo", ApplicationInfo::class.java)
            else {
                @Suppress("DEPRECATION")
                extras?.getParcelable<ApplicationInfo>("android.appInfo")
            }
        }.getOrNull()?.takeIf { it.packageName == packageName && it.uid == notification?.uid }
        label(attached, packageName)?.let { return it }
        return context.getString(R.string.unknown_notification_app)
    }

    private fun label(info: ApplicationInfo?, packageName: String): String? {
        if (info == null) return null
        return runCatching { AppLabels.readable(packages.getApplicationLabel(info), packageName) }.getOrNull()
    }
}
