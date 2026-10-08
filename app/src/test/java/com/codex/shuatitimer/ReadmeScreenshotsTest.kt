package com.codex.shuatitimer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.model.TimerUiStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Renders actual app screens with synthetic fixtures for the repository README. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-port-night-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReadmeScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun closeDatabase() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        AppDatabase.getInstance(compose.activity).close()
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
    }

    @Test fun generateReadmeScreenshots() {
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.waitUntil(10_000) { compose.waitForIdle(); viewModel.setup.value.loaded }
        val database = AppDatabase.getInstance(compose.activity)
        runBlocking(Dispatchers.IO) {
            demoSessions().forEach { database.sessionDao().insert(it) }
        }
        capture("timer-classic")
        compose.runOnUiThread {
            viewModel.setMode(TimerMode.COUNT_DOWN)
            viewModel.setTimerUiStyle(TimerUiStyle.RINGS)
        }
        capture("timer-rings")
        compose.onNodeWithText("记录", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); viewModel.sessions.value.size == 3 }
        capture("records")
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); viewModel.statistics.value.overall.allTimeSessionCount == 3 }
        capture("statistics")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("分模块统计"))
        capture("statistics-modules")
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("倒计时提醒方式").performScrollTo()
        capture("settings")
        compose.onNodeWithText("设备同步").performScrollTo().performClick()
        capture("sync")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val directory = File("../docs/screenshots").canonicalFile
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
