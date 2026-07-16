package dev.lutergs.sgaod.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.lutergs.sgaod.BuildConfig
import dev.lutergs.sgaod.data.source.local.NotificationIconStore
import dev.lutergs.sgaod.data.source.local.NotificationSyncRequester
import dev.lutergs.sgaod.data.source.local.PendingIntentStore
import dev.lutergs.sgaod.domain.model.NotificationInfo
import dev.lutergs.sgaod.domain.usecase.notification.ManageNotificationUseCase
import dev.lutergs.sgaod.domain.usecase.notification.UpdateNotificationSettingsUseCase
import dev.lutergs.sgaod.domain.usecase.settings.SettingsUseCases
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AODNotificationListener : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        fun settingsUseCases(): SettingsUseCases
        fun manageNotificationUseCase(): ManageNotificationUseCase
        fun updateNotificationSettingsUseCase(): UpdateNotificationSettingsUseCase
        fun pendingIntentStore(): PendingIntentStore
        fun notificationIconStore(): NotificationIconStore
        fun notificationSyncRequester(): NotificationSyncRequester
    }

    private var excludedApps: Set<String> = emptySet()
    private var pinnedApps: Set<String> = emptySet()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isSettingsLoaded = false

    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var manageNotificationUseCase: ManageNotificationUseCase
    private lateinit var updateNotificationSettingsUseCase: UpdateNotificationSettingsUseCase
    private lateinit var pendingIntentStore: PendingIntentStore
    private lateinit var notificationIconStore: NotificationIconStore
    private lateinit var notificationSyncRequester: NotificationSyncRequester

    override fun onCreate() {
        super.onCreate()

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java
        )

        settingsUseCases = entryPoint.settingsUseCases()
        manageNotificationUseCase = entryPoint.manageNotificationUseCase()
        updateNotificationSettingsUseCase = entryPoint.updateNotificationSettingsUseCase()
        pendingIntentStore = entryPoint.pendingIntentStore()
        notificationIconStore = entryPoint.notificationIconStore()
        notificationSyncRequester = entryPoint.notificationSyncRequester()

        serviceScope.launch {
            try {
                val maxCount = settingsUseCases.maxNotificationCount.first()
                updateNotificationSettingsUseCase.setMaxNotifications(maxCount)

                val maxPinnedCount = settingsUseCases.maxPinnedNotificationCount.first()
                updateNotificationSettingsUseCase.setMaxPinnedNotifications(maxPinnedCount)

                excludedApps = settingsUseCases.excludedApps.first()

                pinnedApps = settingsUseCases.pinnedApps.first()
                updateNotificationSettingsUseCase.setPinnedApps(pinnedApps)

                isSettingsLoaded = true
                refreshNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial settings", e)
            }
        }

        serviceScope.launch {
            settingsUseCases.excludedApps.collect { apps ->
                excludedApps = apps
                if (isSettingsLoaded) {
                    refreshNotifications()
                }
            }
        }

        serviceScope.launch {
            settingsUseCases.maxNotificationCount.collect { count ->
                updateNotificationSettingsUseCase.setMaxNotifications(count)
                if (isSettingsLoaded) {
                    refreshNotifications()
                }
            }
        }

        serviceScope.launch {
            settingsUseCases.maxPinnedNotificationCount.collect { count ->
                updateNotificationSettingsUseCase.setMaxPinnedNotifications(count)
                if (isSettingsLoaded) {
                    refreshNotifications()
                }
            }
        }

        serviceScope.launch {
            settingsUseCases.pinnedApps.collect { apps ->
                pinnedApps = apps
                updateNotificationSettingsUseCase.setPinnedApps(apps)
                if (isSettingsLoaded) {
                    refreshNotifications()
                }
            }
        }

        // AOD 표시 시점 등 외부에서 요청하는 전체 재동기화.
        // 리스너가 unbind 되었다가 재연결되기 전 놓친 이벤트를 복구한다.
        serviceScope.launch {
            notificationSyncRequester.syncRequests.collect {
                refreshNotifications()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected - resyncing notifications")
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 시스템이 리스너를 unbind 한 경우 재바인딩을 요청하지 않으면
        // 알림 콜백이 영구히 중단되어 AOD 에 오래된 알림이 계속 표시된다.
        Log.w(TAG, "Listener disconnected - requesting rebind")
        try {
            requestRebind(ComponentName(this, AODNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request rebind", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn?.let { statusBarNotification ->
            if (statusBarNotification.packageName in excludedApps) return

            // 그룹 요약 알림은 '표시 가능한 자식이 있는가'에 따라 처리가 달라지므로
            // activeNotifications 전체를 보는 재동기화 경로에 위임한다
            if (statusBarNotification.isGroupSummary()) {
                refreshNotifications()
                return
            }

            val notificationInfo = extractNotificationInfo(statusBarNotification, rankingMap)
            if (notificationInfo != null) {
                registerNotificationResources(statusBarNotification, notificationInfo.key)
                manageNotificationUseCase.add(notificationInfo)
            } else {
                // 같은 key 의 알림이 표시 불가 형태로 '업데이트'된 경우
                // (ongoing 전환 등) 기존 항목을 제거해 이전 내용이 stale 하게 남지 않도록 한다.
                removeNotificationData(statusBarNotification.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { statusBarNotification ->
            removeNotificationData(statusBarNotification.key)
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        super.onNotificationRankingUpdate(rankingMap)
        // 알림 재게시 없이 그룹 재구성/visibility 변경이 일어나는 경로를 커버한다.
        if (isSettingsLoaded) {
            refreshNotifications()
        }
    }

    private fun removeNotificationData(key: String) {
        pendingIntentStore.remove(key)
        notificationIconStore.remove(key)
        manageNotificationUseCase.remove(key)
    }

    private fun refreshNotifications() {
        try {
            val activeNotifications = activeNotifications ?: return
            val rankingMap = currentRanking

            val candidates = activeNotifications.filter { it.packageName !in excludedApps }
            val (summaries, individuals) = candidates.partition { it.isGroupSummary() }

            val notificationList = mutableListOf<NotificationInfo>()
            val displayableGroupKeys = mutableSetOf<String>()

            for (sbn in individuals) {
                val info = extractNotificationInfo(sbn, rankingMap) ?: continue
                registerNotificationResources(sbn, info.key)
                notificationList += info
                displayableGroupKeys += sbn.groupKey
            }

            // 그룹 요약은 표시 가능한 자식이 하나도 없을 때만 표시
            // (일부 앱은 요약에만 콘텐츠를 담아 게시한다 — 무조건 스킵하면 통째로 누락)
            for (sbn in summaries) {
                if (sbn.groupKey in displayableGroupKeys) continue
                val info = extractNotificationInfo(sbn, rankingMap) ?: continue
                registerNotificationResources(sbn, info.key)
                notificationList += info
            }

            manageNotificationUseCase.updateAll(notificationList)

            // 활성 알림에 없는 key 의 아이콘/PendingIntent 정리 (무한 누적 방지)
            val activeKeys = notificationList.mapTo(mutableSetOf()) { it.key }
            pendingIntentStore.retainKeys(activeKeys)
            notificationIconStore.retainKeys(activeKeys)
        } catch (e: Exception) {
            // 리스너 미연결 상태에서 activeNotifications 접근 시 SecurityException 가능
            Log.e(TAG, "Failed to refresh notifications", e)
        }
    }

    private fun registerNotificationResources(sbn: StatusBarNotification, key: String) {
        sbn.notification?.contentIntent?.let {
            pendingIntentStore.register(key, it)
        }
        sbn.notification?.smallIcon?.let {
            notificationIconStore.register(key, it)
        }
    }

    private fun StatusBarNotification.isGroupSummary(): Boolean {
        val flags = notification?.flags ?: return false
        return flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    private fun extractNotificationInfo(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?
    ): NotificationInfo? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        if (sbn.isOngoing) return null

        val appName = getAppName(sbn)

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isEmpty() && content.isEmpty()) return null

        val isContentVisible = getContentVisibility(sbn, rankingMap, notification)

        return NotificationInfo(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            content = content,
            postTime = sbn.postTime,
            isContentVisible = isContentVisible,
            conversationKey = conversationKeyOf(sbn, notification, title)
        )
    }

    /**
     * 대화(채팅방/채널) 단위 그룹핑 키를 계산한다.
     *
     * 메시징 앱은 방마다 별도 알림을 게시하므로, OneUI 잠금화면처럼 방별로
     * 구분해 표시하려면 packageName 이 아닌 대화 식별자로 그룹핑해야 한다.
     * 우선순위:
     * 1. shortcutId — Android 11+ 대화 알림의 공식 식별자 (카카오톡/Slack 등)
     * 2. EXTRA_CONVERSATION_TITLE — 그룹 대화 제목
     * 3. MessagingStyle 알림의 title — 위 둘이 없는 메시징 앱 폴백 (보통 방/발신자 이름)
     * 4. 그 외 앱: packageName (기존과 동일한 앱 단위 그룹핑)
     */
    private fun conversationKeyOf(
        sbn: StatusBarNotification,
        notification: Notification,
        title: String
    ): String {
        val shortcutId = notification.shortcutId
        if (!shortcutId.isNullOrBlank()) {
            return "${sbn.packageName}|s:$shortcutId"
        }

        val conversationTitle = notification.extras
            ?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (!conversationTitle.isNullOrBlank()) {
            return "${sbn.packageName}|c:$conversationTitle"
        }

        val isMessagingStyle = notification.extras
            ?.getCharSequence(Notification.EXTRA_TEMPLATE)?.toString()
            ?.endsWith("MessagingStyle") == true
        if (isMessagingStyle && title.isNotBlank()) {
            return "${sbn.packageName}|t:$title"
        }

        return sbn.packageName
    }

    /**
     * 사용자가 설정 앱에서 지정한 잠금화면 알림 표시 정책을 확인합니다.
     *
     * 우선순위: 사용자 앱별 override > 채널 lockscreenVisibility > 시스템 전역 설정
     */
    private fun getContentVisibility(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
        notification: Notification
    ): Boolean {
        val rawShowNotifications = Settings.Secure.getInt(
            contentResolver,
            "lock_screen_show_notifications",
            SETTING_READ_FAILED
        )
        val rawAllowPrivate = Settings.Secure.getInt(
            contentResolver,
            "lock_screen_allow_private_notifications",
            SETTING_READ_FAILED
        )

        val showNotificationsOnLockscreen =
            if (rawShowNotifications == SETTING_READ_FAILED) true else rawShowNotifications == 1
        val allowPrivateNotifications =
            if (rawAllowPrivate == SETTING_READ_FAILED) true else rawAllowPrivate == 1

        if (!showNotificationsOnLockscreen) return false

        var visibilityOverride: Int = VISIBILITY_NO_OVERRIDE
        var channelVisibility: Int = VISIBILITY_NO_OVERRIDE

        if (rankingMap != null) {
            val ranking = Ranking()
            if (rankingMap.getRanking(sbn.key, ranking)) {
                visibilityOverride = ranking.lockscreenVisibilityOverride
                ranking.channel?.let { channel ->
                    channelVisibility = channel.lockscreenVisibility
                }
            }
        }

        val effectiveVisibility = if (visibilityOverride != VISIBILITY_NO_OVERRIDE) {
            visibilityOverride
        } else {
            channelVisibility
        }

        val result = when (effectiveVisibility) {
            Notification.VISIBILITY_PUBLIC -> true
            Notification.VISIBILITY_SECRET -> false
            Notification.VISIBILITY_PRIVATE -> false
            else -> allowPrivateNotifications
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Visibility for ${sbn.packageName}: override=$visibilityOverride, " +
                    "channel=$channelVisibility, notif=${notification.visibility} -> $result"
            )
        }

        return result
    }

    private fun getAppName(sbn: StatusBarNotification): String {
        val packageName = sbn.packageName
        val notification = sbn.notification
        val packageManager = applicationContext.packageManager

        // 1. Notification extras에서 앱 이름 확인 (substName)
        notification?.extras?.getCharSequence("android.substName")?.toString()?.let { substName ->
            if (substName.isNotBlank()) {
                return substName
            }
        }

        // 2. PackageManager로 앱 이름 조회
        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
            return packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            // 아래 폴백으로 진행
        }

        // 3. getPackageInfo로 재시도
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
            packageInfo.applicationInfo?.let { appInfo ->
                return packageManager.getApplicationLabel(appInfo).toString()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // 최종 폴백으로 진행
        }

        // 4. 최종 폴백: 패키지명 마지막 부분 사용
        val simpleName = packageName.substringAfterLast('.')
        return simpleName.replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val TAG = "AODNotificationListener"
        private const val VISIBILITY_NO_OVERRIDE = -1000
        private const val SETTING_READ_FAILED = -999
    }
}
