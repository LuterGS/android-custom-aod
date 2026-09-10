package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class NotificationPrivacyTest {
    @Test fun optInRequiredEvenForPublicMessages() {
        assertFalse(NotificationPrivacy.showContent(false, true, true, NotificationPrivacy.PUBLIC))
    }
    @Test fun systemGlobalHideWins() {
        for (visibility in -1..1) assertFalse(NotificationPrivacy.showContent(true, false, true, visibility))
    }
    @Test fun secretIsNeverRevealed() {
        assertFalse(NotificationPrivacy.showContent(true, true, true, NotificationPrivacy.SECRET))
    }
    @Test fun privateHonorsSystemSetting() {
        assertFalse(NotificationPrivacy.showContent(true, true, false, NotificationPrivacy.PRIVATE))
        assertTrue(NotificationPrivacy.showContent(true, true, true, NotificationPrivacy.PRIVATE))
    }
    @Test fun publicContentCanBeShownAfterOptIn() {
        assertTrue(NotificationPrivacy.showContent(true, true, false, NotificationPrivacy.PUBLIC))
    }
    @Test fun rankingOverrideWinsOverChannelAndNotification() {
        assertEquals(NotificationPrivacy.PRIVATE, NotificationPrivacy.visibility(NotificationPrivacy.PUBLIC, NotificationPrivacy.SECRET, NotificationPrivacy.PRIVATE))
    }
    @Test fun channelOverrideWinsOverNotification() {
        assertEquals(NotificationPrivacy.SECRET, NotificationPrivacy.visibility(NotificationPrivacy.PUBLIC, NotificationPrivacy.SECRET, null))
    }
    @Test fun missingOverridesUseNotificationVisibility() {
        assertEquals(NotificationPrivacy.SECRET, NotificationPrivacy.visibility(NotificationPrivacy.SECRET, null, null))
    }
}
