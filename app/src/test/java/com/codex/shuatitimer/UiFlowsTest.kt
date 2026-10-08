package com.codex.shuatitimer

import android.content.Intent
import android.app.Activity
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.codex.shuatitimer.sync.SyncCodec
import com.codex.shuatitimer.sync.*
import kotlinx.coroutines.*
import java.io.File
import com.codex.shuatitimer.data.db.AppDatabase
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FakeNsdManager::class])
@LooperMode(LooperMode.Mode.PAUSED)
class UiFlowsTest {
    @get:Rule(order = 0) val freshApplicationFactory = object : ExternalResource() {
        override fun before() {
            // Robolectric replaces Application between methods, while AndroidX caches
            // its factory. Clear that cache before the activity uses the new context.
            ViewModelProvider.AndroidViewModelFactory::class.java.declaredFields
                .single { java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.type == ViewModelProvider.AndroidViewModelFactory::class.java }
                .apply { isAccessible = true }.set(null, null)
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun countdownReminderButtonsSaveTheSelectedMode() {
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        awaitState(10_000) { viewModel.setup.value.loaded }
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("消息弹窗").performScrollTo().assertIsSelected()
        compose.onNodeWithText("闹钟提醒").performScrollTo().performClick()
        compose.onNodeWithText("闹钟提醒").assertIsSelected()
        assertEquals(com.codex.shuatitimer.data.model.CountdownReminderMode.ALARM,
            com.codex.shuatitimer.data.preferences.CountdownReminderSettings(compose.activity).mode)
        compose.onNodeWithText("消息弹窗").performClick()
        compose.onNodeWithText("消息弹窗").assertIsSelected()
        assertEquals(com.codex.shuatitimer.data.model.CountdownReminderMode.MESSAGE,
            viewModel.setup.value.countdownReminderMode)
    }

    @After fun closeDatabaseBeforeRobolectricResetsConnections() {
        val activity = compose.activity
        compose.runOnUiThread { activity.viewModelStore.clear() }
        AppDatabase.getInstance(activity).close()
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
    }

    private fun awaitState(timeout: Long, condition: () -> Boolean) {
        compose.waitUntil(timeout) {
            compose.waitForIdle()
            condition()
        }
    }

    private fun seedDemoSessions() {
        val dao = AppDatabase.getInstance(compose.activity).sessionDao()
        runBlocking(Dispatchers.IO) { demoSessions().forEach { dao.insert(it) } }
    }

    private fun openSyncScreen() {
        compose.onNode(hasText("设置") and hasAnyAncestor(isSelectable()), useUnmergedTree = true).performClick()
        compose.onNodeWithText("设备同步").performScrollTo().performClick()
    }

    @Test fun recordsModuleManagementAndStatisticsAreUsable() {
        seedDemoSessions()
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        awaitState(10_000) { viewModel.setup.value.loaded }
        compose.onNodeWithText("题刻").assertIsDisplayed()
        compose.onNodeWithText("记录", useUnmergedTree = true).performClick()
        awaitState(10_000) { viewModel.sessions.value.size == 3 }
        compose.onNodeWithText("共 3 次练习，每一次都有迹可循。").assertIsDisplayed()
        compose.onNodeWithText("12:00").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("分模块统计"))
        compose.onNodeWithText("分模块统计").assertIsDisplayed()
        compose.onNodeWithText("36.0 秒").assertExists()
        compose.onNodeWithText("85.0%").assertExists()
        compose.onNodeWithText("60.0 秒").assertExists()
        compose.onNodeWithText("80.0%").assertExists()
        compose.onNodeWithText("30.0 秒").assertExists()
        compose.onNodeWithText("90.0%").assertExists()

        compose.onNodeWithText("计时", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("添加 / 管理模块"))
        compose.onNodeWithText("添加 / 管理模块").performClick()
        compose.onNodeWithText("新模块名称").performTextInput("图形专项测试")
        compose.onNodeWithText("添加并保存").performClick()
        awaitState(10_000) { "图形专项测试" in viewModel.setup.value.modules }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("图形专项测试").assertExists()
        assertEquals("图形专项测试", viewModel.setup.value.selectedSubject)
        compose.onNodeWithText("添加 / 管理模块").performClick()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
            .performScrollToNode(hasContentDescription("删除模块 图形专项测试"))
        compose.onNodeWithContentDescription("删除模块 图形专项测试").performScrollTo().performClick()
        compose.onNodeWithText("删除模块", useUnmergedTree = true).performClick()
        awaitState(10_000) { "图形专项测试" !in viewModel.setup.value.modules }
        compose.onNodeWithText("完成").performClick()
        assertEquals(3, viewModel.sessions.value.size)
    }

    @Test @Config(sdk = [34], qualifiers = "night")
    fun darkNavigationLabelsRemainReadableWhenSelectedAndUnselected() {
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        awaitState(10_000) { viewModel.setup.value.loaded }
        fun checkLabels() {
            listOf("计时", "记录", "统计", "设置").forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(label) and hasAnyAncestor(isSelectable()), useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val foreground = layouts.single().layoutInput.style.color
                val contrast = (foreground.luminance() + 0.05f) / (Color(0xFF18251F).luminance() + 0.05f)
                assertTrue("$label must meet 4.5:1 contrast; was $contrast", contrast >= 4.5f)
            }
        }
        checkLabels()
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        checkLabels()
    }

    @Test fun lanReceiverAndSyncFileActionsAreUsable() {
        seedDemoSessions()
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        awaitState(10_000) { viewModel.setup.value.loaded }
        openSyncScreen()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("搜索附近设备"))
        compose.onNodeWithText("搜索附近设备").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitState(10_000) { viewModel.sync.value.host != null }
        assertNotNull(viewModel.sync.value.message, viewModel.sync.value.host)
        compose.onNodeWithText("本机").assertExists()
        compose.onNodeWithText("停止搜索").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitState(5_000) { !viewModel.sync.value.busy }
        assertNull(viewModel.sync.value.host)

        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(4)
        val exportUri = Uri.parse("content://ui-test/export.json")
        val bytes = ByteArrayOutputStream()
        shadowOf(compose.activity.contentResolver).registerOutputStream(exportUri, bytes)
        compose.onNodeWithText("导出同步文件").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        val export = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.action)
        assertEquals("application/json", export.type)
        compose.runOnUiThread {
            shadowOf(compose.activity).receiveResult(export, Activity.RESULT_OK, Intent().setData(exportUri))
        }
        awaitState(10_000) { viewModel.sync.value.message.startsWith("同步文件已导出") }
        val exported = SyncCodec.decode(bytes.toString("UTF-8"))
        assertEquals(3, exported.sessions.count { !it.deleted })
        val importUri = Uri.parse("content://ui-test/import.json")
        shadowOf(compose.activity.contentResolver).registerInputStream(importUri, ByteArrayInputStream(bytes.toByteArray()))
        compose.onNodeWithText("导入并合并").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        val import = shadowOf(compose.activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, import.action)
        compose.runOnUiThread {
            shadowOf(compose.activity).receiveResult(import, Activity.RESULT_OK, Intent().setData(importUri))
        }
        awaitState(10_000) { viewModel.sync.value.pendingImport != null }
        compose.onNodeWithText("确认合并").performClick()
        awaitState(10_000) { viewModel.sync.value.message.startsWith("导入完成") }
        compose.onNodeWithText("记录", useUnmergedTree = true).performClick()
        awaitState(10_000) { viewModel.sessions.value.size == 3 }
    }

    @Test fun incomingPairingRequiresConsentAndCanBeForgottenFromTheScreen() {
        seedDemoSessions()
        val viewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        awaitState(10_000) { viewModel.setup.value.loaded }
        openSyncScreen()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("搜索附近设备"))
        compose.onNodeWithText("搜索附近设备").performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitState(10_000) { viewModel.sync.value.host != null }
        // Use the exact credential store held by the running ViewModel. A separate store instance
        // can race its on-disk initialization in Robolectric and make the test target stale.
        val pairingField = AppViewModel::class.java.getDeclaredField("pairings").apply { isAccessible = true }
        val local = pairingField.get(viewModel) as PairingStore
        val identity = local.identity()
        val target = NearbyDevice(identity.id, identity.name, "127.0.0.1", viewModel.sync.value.host!!.port)
        val other = PairingStore(File(compose.activity.cacheDir, "pairing-ui-peer-${java.util.UUID.randomUUID()}"), "测试平板")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = DeviceSync(other)
        try {
            val rejected = scope.async { runCatching { transport.connect(target, SyncBundle(emptyList(), emptyList())) {} } }
            awaitState(10_000) { viewModel.sync.value.pairingRequest != null || rejected.isCompleted }
            assertNotNull("Pairing prompt missing: ${viewModel.sync.value.message}; " +
                if (rejected.isCompleted) runBlocking { rejected.await() }.exceptionOrNull() else "waiting",
                viewModel.sync.value.pairingRequest)
            compose.onNodeWithText("允许配对？").assertIsDisplayed()
            compose.onNodeWithText("拒绝").performSemanticsAction(SemanticsActions.OnClick) { it() }
            awaitState(10_000) { rejected.isCompleted }
            assertTrue(runBlocking { rejected.await() }.isFailure)
            assertTrue(viewModel.sync.value.paired.isEmpty())
            val accepted = scope.async { transport.connect(target, SyncBundle(emptyList(), emptyList())) {} }
            awaitState(10_000) { viewModel.sync.value.pairingRequest != null }
            compose.onNodeWithText("同意配对").performSemanticsAction(SemanticsActions.OnClick) { it() }
            awaitState(10_000) { accepted.isCompleted && viewModel.sync.value.paired.size == 1 }
            assertEquals(3, runBlocking { accepted.await() }.sessions.count { !it.deleted })
            assertEquals("测试平板", viewModel.sync.value.paired.single().name)
            viewModel.forgetDevice(viewModel.sync.value.paired.single().id)
            assertTrue(viewModel.sync.value.paired.isEmpty())
            assertTrue(local.devices().isEmpty())
        } finally { transport.stop(); scope.cancel() }
    }
}
