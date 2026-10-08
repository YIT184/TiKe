package com.codex.shuatitimer.service

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerServiceChannelTest {
    @Test
    fun liveTimerChannelIsHighImportanceAndSilent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(TimerService::class.java).create()
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(TimerService.CHANNEL_TIMER)

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)

        controller.destroy()
    }

    @Test
    fun appDeclaresUserControlledOverlayPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        assertTrue(
            packageInfo.requestedPermissions.orEmpty()
                .contains(Manifest.permission.SYSTEM_ALERT_WINDOW)
        )
    }
}
