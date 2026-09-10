package dev.lutergs.sgaod.domain

import org.junit.Assert.*
import org.junit.Test

class AppLabelsTest {
    @Test fun localizedOfficialNameIsPreserved() {
        assertEquals("카카오톡", AppLabels.readable("카카오톡", "com.kakao.talk"))
        assertEquals("YouTube Music", AppLabels.readable("YouTube Music", "com.google.android.apps.youtube.music"))
    }
    @Test fun packageFallbackIsNotAName() {
        assertNull(AppLabels.readable(" com.example.app ", "com.example.app"))
    }
    @Test fun missingNameAllowsAnotherSource() {
        assertNull(AppLabels.readable(null, "com.example.app"))
        assertNull(AppLabels.readable(" \n ", "com.example.app"))
    }
    @Test fun labelCannotInjectLinesOrBidiOverrides() {
        assertEquals("Example App", AppLabels.readable("Example\nApp", "com.example.app"))
        assertEquals("Example", AppLabels.readable("\u202eExample\u202c", "com.example.app"))
    }
}
