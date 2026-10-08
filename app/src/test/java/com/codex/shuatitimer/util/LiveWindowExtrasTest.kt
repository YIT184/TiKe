package com.codex.shuatitimer.util

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.codex.shuatitimer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveWindowExtrasTest {
    @Test
    fun notificationContainsHarmonyTimerAndCapsuleFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = NotificationCompat.Builder(context, "test")
            .setSmallIcon(R.drawable.ic_stat_timer)

        LiveWindowExtras.apply(
            context = context,
            builder = builder,
            operation = LiveWindowExtras.Operation.CREATE,
            title = "资料分析",
            content = "25:00",
            iconResId = R.drawable.ic_stat_timer
        )

        val extras = builder.build().extras
        val capsule = extras.getBundle("notification.live.capsule")

        assertEquals(0, extras.getInt("notification.live.operation"))
        assertEquals("TIMER", extras.getString("notification.live.event"))
        assertEquals(1, extras.getInt("notification.live.type"))
        assertNotNull(capsule)
        assertEquals(1, capsule?.getInt("notification.live.capsuleStatus"))
        assertEquals(1, capsule?.getInt("notification.live.capsuleType"))
        assertEquals("资料分析", capsule?.getString("notification.live.capsuleTitle"))
        assertEquals("25:00", capsule?.getString("notification.live.capsuleContent"))
    }
}
