package dev.lutergs.sgaod.service

import android.app.Notification
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.lutergs.sgaod.aodStore
import dev.lutergs.sgaod.data.AppLabelResolver
import dev.lutergs.sgaod.domain.NotificationEntry
import dev.lutergs.sgaod.domain.NotificationPrivacy
import dev.lutergs.sgaod.domain.SleepReason

class AODNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val labels by lazy { AppLabelResolver(this) }
    private var connected = false
    private var observing = false
    private val refresh = Runnable { publish() }
    private val privacyObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { publish() }
    }
    override fun onListenerConnected() {
        connected = true
        aodStore.listenerConnected = true
        aodStore.refreshNotifications = { schedule() }
        if (!observing) {
            listOf("lock_screen_show_notifications", "lock_screen_allow_private_notifications").forEach {
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, privacyObserver)
            }
            observing = true
        }
        publish()
        aodStore.notifyChanged()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) = schedule()
    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) = schedule()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = schedule()
    private fun schedule() {
        val demand = aodStore.settingsScreenVisible || (aodStore.session && aodStore.sleepReason == SleepReason.NONE)
        if (connected && demand && !handler.hasCallbacks(refresh)) handler.postDelayed(refresh, 300)
        if (!aodStore.settings.enabled) aodStore.updateContent(aodStore.content.copy(notifications = emptyList()))
    }
    private fun publish() {
        if (!connected) return
        val settings = aodStore.settings
        val show = Settings.Secure.getInt(contentResolver, "lock_screen_show_notifications", 0) == 1
        val privateAllowed = Settings.Secure.getInt(contentResolver, "lock_screen_allow_private_notifications", 0) == 1
        val icons = mutableMapOf<String, Icon>()
        val entries = if (!show || !settings.enabled) emptyList() else try {
            val notifications = activeNotifications.orEmpty().filter {
                it.packageName != packageName && it.packageName !in settings.excludedPackages &&
                    it.notification.category != Notification.CATEGORY_TRANSPORT
            }
            val childGroups = notifications.filter {
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }.mapTo(hashSetOf()) { it.groupKey }
            notifications.asSequence().filter {
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 || it.groupKey !in childGroups
            }.mapNotNull { sbn ->
                val notification = sbn.notification
                val ranking = Ranking()
                val ranked = currentRanking.getRanking(sbn.key, ranking)
                if (ranked && (ranking.isSuspended || ranking.importance == 0)) return@mapNotNull null
                val noOverride = -1000
                val visibility = NotificationPrivacy.visibility(notification.visibility,
                    if (ranked) ranking.channel?.lockscreenVisibility?.takeUnless { it == noOverride } else null,
                    if (ranked) ranking.lockscreenVisibilityOverride.takeUnless { it == noOverride } else null)
                if (visibility == Notification.VISIBILITY_SECRET) return@mapNotNull null
                val visible = NotificationPrivacy.showContent(settings.showNotificationContent, show, privateAllowed, visibility)
                val name = labels.resolve(sbn.packageName, sbn)
                notification.smallIcon?.takeIf {
                    it.type == Icon.TYPE_RESOURCE || it.type == Icon.TYPE_BITMAP || it.type == Icon.TYPE_ADAPTIVE_BITMAP
                }?.let { icons[sbn.key] = it }
                // Raw private text never enters shared state when redacted.
                NotificationEntry(sbn.key, sbn.packageName, name.take(80),
                    if (visible) notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(160) else "",
                    if (visible) (notification.extras.getCharSequence(Notification.EXTRA_TEXT)
                        ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty().take(240) else "",
                    sbn.postTime)
            }.sortedByDescending { it.postedAt }.take(50).toList()
        } catch (_: SecurityException) { emptyList() }
        val keys = entries.mapTo(hashSetOf()) { it.key }
        aodStore.updateNotifications(entries, icons.filterKeys { it in keys })
    }
    override fun onListenerDisconnected() {
        clear()
        if (aodStore.settings.enabled) requestRebind(ComponentName(this, AODNotificationListener::class.java))
    }
    private fun clear() {
        connected = false
        handler.removeCallbacksAndMessages(null)
        aodStore.listenerConnected = false
        aodStore.refreshNotifications = null
        aodStore.updateContent(aodStore.content.copy(notifications = emptyList()))
        aodStore.notifyChanged()
    }
    override fun onDestroy() {
        clear()
        if (observing) contentResolver.unregisterContentObserver(privacyObserver)
        super.onDestroy()
    }
}
