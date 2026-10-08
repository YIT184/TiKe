package com.codex.shuatitimer

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.codex.shuatitimer.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityLaunchTest {
    @Test
    fun activityCreatesAndRendersInitialFrame() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        try {
            val viewModel = ViewModelProvider(activity)[AppViewModel::class.java]
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!viewModel.setup.value.loaded && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue(viewModel.setup.value.loaded)
            assertFalse(activity.isFinishing)
        } finally {
            activity.viewModelStore.clear()
            controller.pause().stop().destroy()
            AppDatabase.getInstance(activity).close()
            AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
        }
    }
}
