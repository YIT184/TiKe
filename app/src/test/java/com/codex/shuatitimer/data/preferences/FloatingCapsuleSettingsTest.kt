package com.codex.shuatitimer.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FloatingCapsuleSettingsTest {
    @Test
    fun enabledAndDraggedPositionArePersisted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = FloatingCapsuleSettings(context)
        settings.enabled = false

        assertFalse(settings.enabled)

        settings.enabled = true
        settings.savePosition(120, 240)
        val restored = FloatingCapsuleSettings(context)

        assertTrue(restored.enabled)
        assertEquals(FloatingCapsuleSettings.Position(120, 240), restored.savedPosition())
    }
}
