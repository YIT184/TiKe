package com.codex.shuatitimer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.codex.shuatitimer.TimerSetupUiState
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.ui.theme.ShuatiTimerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class RingTimerSetupTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun doubleTapSetsExactCustomMinutesAndSlowHorizontalSwipesCommit() {
        val setup = mutableStateOf(TimerSetupUiState(
            mode = TimerMode.COUNT_DOWN, selectedSubject = "资料分析",
            modules = listOf("资料分析", "言语理解"), loaded = true))
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            ShuatiTimerTheme {
                RingTimerSetupContent(setup.value, PaddingValues(0.dp),
                    onModeChange = {}, onSubjectChange = {},
                    onDurationChange = { setup.value = setup.value.copy(countdownMinutes = it) },
                    onPlannedQuestionsChange = {}, onAddModule = {}, onDeleteModule = {}, onStart = {})
            }
        }
        compose.onNodeWithText("30 分").performTouchInput { doubleClick() }
        compose.onNodeWithText("自定义倒计时").assertIsDisplayed()
        compose.onNodeWithTag("custom_duration_input").performTextReplacement("17")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("17 分").assertIsDisplayed()
        assertEquals(17, setup.value.countdownMinutes)

        compose.onNodeWithTag("timer_rings").performTouchInput {
            val top = Offset(center.x, center.y - min(width, height) * 0.47f * 0.552f)
            swipe(top, top + Offset(52f * density, 0f), durationMillis = 1_000)
        }
        compose.onNodeWithText("22 分").assertIsDisplayed()
        assertEquals(22, setup.value.countdownMinutes)
    }

    @Test
    fun singleTapsAndCancelledOrInvalidInputsKeepTheExistingTime() {
        val setup = mutableStateOf(TimerSetupUiState(
            mode = TimerMode.COUNT_DOWN, selectedSubject = "资料分析",
            modules = listOf("资料分析"), loaded = true))
        compose.setContent {
            ShuatiTimerTheme {
                RingTimerSetupContent(setup.value, PaddingValues(0.dp),
                    onModeChange = {}, onSubjectChange = {},
                    onDurationChange = { setup.value = setup.value.copy(countdownMinutes = it) },
                    onPlannedQuestionsChange = {}, onAddModule = {}, onDeleteModule = {}, onStart = {})
            }
        }
        compose.onNodeWithText("30 分").performTouchInput { click() }
        compose.onNodeWithText("自定义倒计时").assertDoesNotExist()
        compose.onNodeWithText("30 分").performTouchInput { doubleClick() }
        compose.onNodeWithTag("custom_duration_input").performTextReplacement("361")
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithTag("custom_duration_input").performTextReplacement("4")
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onNodeWithTag("custom_duration_input").performTextReplacement("27")
        compose.onNodeWithText("确定").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("30 分").assertIsDisplayed()
        assertEquals(30, setup.value.countdownMinutes)
    }
}
