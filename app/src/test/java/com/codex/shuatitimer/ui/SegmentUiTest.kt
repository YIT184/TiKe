package com.codex.shuatitimer.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.codex.shuatitimer.FinishDialogState
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.*
import com.codex.shuatitimer.ui.theme.ShuatiTimerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h851dp-port-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SegmentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val snapshot = TimerStateStore.Snapshot(active = true, running = true,
        mode = TimerMode.COUNT_DOWN, subject = "资料分析", startWallClock = 1_000L,
        targetDurationMs = 1_800_000L, effectiveElapsedMs = 1_320_000L, sessionId = "8183f986-e03a-4b09-b6cb-a26de88da427",
        segmentBoundaries = listOf(SegmentBoundary(600_000L, 601_000L, 0)))

    @Test fun finishSheetSelectsEachModuleCreatesAModuleAndKeepsTheSaveButtonVisible() {
        val modules = mutableStateOf(listOf("资料分析", "言语理解"))
        var saved: List<Session> = emptyList()
        compose.setContent {
            ShuatiTimerTheme(darkTheme = true) {
                FinishSegmentsSheet(FinishDialogState(visible = true, snapshot = snapshot, finishWallClock = 1_321_000L),
                    modules.value, onDismiss = {},
                    onCreateModule = { name, done -> modules.value = modules.value + name; done(null) },
                    onSave = { results, note ->
                        saved = SegmentSessionBuilder.build(snapshot, 1_321_000L, results, modules.value, note); null
                    })
            }
        }
        compose.onNodeWithTag("save_segments").assertIsDisplayed()
        // Assign the first segment from the module picker.
        compose.onNodeWithTag("segment_module_0").performClick()
        compose.onNodeWithText("资料分析").performClick()
        // Create a module from the second segment picker. The picker switches to the
        // inline creation form instead of opening a second dialog.
        compose.onNodeWithTag("segment_module_1").performScrollTo().performClick()
        compose.onNodeWithTag("open_new_segment_module").performClick()
        compose.onNodeWithTag("new_segment_module").performTextInput("专项训练")
        compose.onNodeWithText("新建并使用").performClick()
        compose.onNodeWithText("专项训练").assertExists()
        compose.onNodeWithText("2 / 2 已选择").assertIsDisplayed()
        // Fill the optional counts last: no dialog needs to be opened afterwards.
        compose.onNodeWithTag("segment_questions_0").performScrollTo().performTextInput("20")
        compose.onNodeWithTag("segment_correct_0").performTextInput("17")
        compose.onNodeWithTag("save_segments").assertIsDisplayed().performClick()
        assertEquals(listOf("资料分析", "专项训练"), saved.map { it.subject })
        assertEquals(listOf(600_000L, 720_000L), saved.map { it.effectiveElapsedMs })
        assertEquals(17, saved.first().correctCount)
        assertNull(saved.last().questionCount)
    }

    @Test fun activeRingLayoutShowsSegmentsAndOneTapSilence() {
        var silenced = false
        var undone = false
        compose.setContent {
            ShuatiTimerTheme(darkTheme = true) {
                ArtfulBackground {
                    RingActiveTimerContent(snapshot.copy(alarmRinging = true, effectiveElapsedMs = 1_920_000),
                        PaddingValues(0.dp), onPause = {}, onResume = {}, onSplit = { true },
                        onUndoSplit = { undone = true }, onSilence = { silenced = true }, onFinish = {}, onDiscard = {})
                }
            }
        }
        compose.onNodeWithTag("silence_alarm").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(silenced)
        compose.onNodeWithTag("undo_split").performScrollTo().performClick()
        assertTrue(undone)
        compose.onNodeWithTag("split_timer").performScrollTo().assertIsEnabled()
    }

    @Test fun lightModeAndLongModuleNamesRemainReadableAndPreviousModuleCanBeReused() {
        val modules = listOf("较长的自定义模块名称用于界面显示测试", "资料分析")
        compose.setContent {
            ShuatiTimerTheme(darkTheme = false) {
                FinishSegmentsSheet(FinishDialogState(visible = true, snapshot = snapshot, finishWallClock = 1_321_000L),
                    modules, onDismiss = {}, onCreateModule = { _, _ -> }, onSave = { _, _ -> null })
            }
        }
        compose.onNodeWithTag("segment_module_0").performClick()
        compose.onNodeWithText(modules.first()).performClick()
        compose.onNodeWithText("沿用上一段 · ${modules.first()}").performScrollTo().performClick()
        compose.onNodeWithText("2 / 2 已选择").assertIsDisplayed()
        compose.onNodeWithTag("save_segments").assertIsDisplayed()
    }
}
