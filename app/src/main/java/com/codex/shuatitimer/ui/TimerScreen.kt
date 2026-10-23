package com.codex.shuatitimer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.TimerSetupUiState
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.data.model.TimerUiStyle
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.TimeUtils

@Composable
fun TimerScreen(viewModel: AppViewModel, scaffoldPadding: PaddingValues) {
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val timer by viewModel.timer.collectAsStateWithLifecycle()
    val finishDialog by viewModel.finishDialog.collectAsStateWithLifecycle()

    ArtfulBackground {
        if (timer.active) {
            if (setup.timerUiStyle == TimerUiStyle.RINGS) {
                RingActiveTimerContent(
                    snapshot = timer,
                    scaffoldPadding = scaffoldPadding,
                    onPause = viewModel::pauseTimer,
                    onResume = viewModel::resumeTimer,
                    onSplit = viewModel::splitTimer,
                    onUndoSplit = viewModel::undoSplit,
                    onSilence = viewModel::silenceAlarm,
                    onFinish = viewModel::requestFinish,
                    onDiscard = viewModel::discardTimer
                )
            } else {
                ActiveTimerContent(
                    snapshot = timer,
                    scaffoldPadding = scaffoldPadding,
                    onPause = viewModel::pauseTimer,
                    onResume = viewModel::resumeTimer,
                    onSplit = viewModel::splitTimer,
                    onUndoSplit = viewModel::undoSplit,
                    onSilence = viewModel::silenceAlarm,
                    onFinish = viewModel::requestFinish,
                    onDiscard = viewModel::discardTimer
                )
            }
        } else {
            if (setup.timerUiStyle == TimerUiStyle.RINGS) {
                RingTimerSetupContent(
                    setup = setup,
                    scaffoldPadding = scaffoldPadding,
                    onModeChange = viewModel::setMode,
                    onSubjectChange = viewModel::setSubject,
                    onDurationChange = viewModel::setCountdownMinutes,
                    onPlannedQuestionsChange = viewModel::setPlannedQuestions,
                    onAddModule = viewModel::addModule,
                    onDeleteModule = viewModel::deleteModule,
                    onStart = { viewModel.startTimer()?.let(viewModel::showMessage) }
                )
            } else {
                TimerSetupContent(
                    setup = setup,
                    scaffoldPadding = scaffoldPadding,
                    viewModel = viewModel
                )
            }
        }
    }

    if (finishDialog.visible) {
        FinishSegmentsSheet(
            state = finishDialog,
            modules = setup.modules,
            onDismiss = viewModel::cancelFinish,
            onCreateModule = viewModel::addSegmentModule,
            onSave = viewModel::saveFinishedSegments
        )
    }
}

@Composable
private fun TimerSetupContent(
    setup: TimerSetupUiState,
    scaffoldPadding: PaddingValues,
    viewModel: AppViewModel
) {
    var showModules by remember { mutableStateOf(false) }
    val moduleListState = rememberLazyListState()
    val moduleRowReady by remember { derivedStateOf { moduleListState.layoutInfo.totalItemsCount > 0 } }
    LaunchedEffect(setup.selectedSubject, setup.modules, moduleRowReady) {
        val index = setup.modules.indexOf(setup.selectedSubject)
        if (moduleRowReady && index >= 0 && moduleListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            moduleListState.animateScrollToItem(index)
        }
    }
    if (showModules) {
        ModuleManager(setup.modules, viewModel::addModule, viewModel::deleteModule) { showModules = false }
    }
    val quickMinutes = listOf(15, 30, 45, 60, 120)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = scaffoldPadding.calculateTopPadding() + 24.dp,
            end = 20.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "Focus journal",
                title = "题刻",
                subtitle = ""
            )
        }
        item {
            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionTitle("01", "计时方式")
                    ModeSelector(setup.mode, viewModel::setMode)
                    AnimatedVisibility(setup.mode == TimerMode.COUNT_DOWN) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "本轮时长",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickMinutes) { minutes ->
                                    AssistChip(
                                        onClick = { viewModel.setCountdownMinutes(minutes) },
                                        label = { Text("${minutes}分钟") },
                                        leadingIcon = if (setup.countdownMinutes == minutes) {
                                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                        } else null,
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (setup.countdownMinutes == minutes) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else MaterialTheme.colorScheme.surface
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            enabled = true,
                                            borderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                            DurationStepper(
                                minutes = setup.countdownMinutes,
                                onChange = viewModel::setCountdownMinutes
                            )
                        }
                    }
                }
            }
        }
        item {
            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SectionTitle("02", "练习模块")
                    LazyRow(state = moduleListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(setup.modules, key = { it }) { subject ->
                            val selected = setup.selectedSubject == subject
                            AssistChip(
                                onClick = { viewModel.setSubject(subject) },
                                label = { Text(subject) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else MaterialTheme.colorScheme.surface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                    TextButton(onClick = { showModules = true }, enabled = setup.loaded) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加 / 管理模块")
                    }
                    if (setup.loaded && setup.modules.isEmpty()) {
                        Text("请先添加一个模块，再开始练习。")
                    }
                    OutlinedTextField(
                        value = setup.plannedQuestions?.toString().orEmpty(),
                        onValueChange = { input ->
                            val digits = input.filter(Char::isDigit).take(4)
                            viewModel.setPlannedQuestions(digits.toIntOrNull())
                        },
                        label = { Text("计划题数（选填）") },
                        placeholder = { Text("例如：20") },
                        suffix = { Text("题") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.startTimer()?.let(viewModel::showMessage)
                },
                enabled = setup.loaded && setup.modules.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Outlined.PlayArrow, null)
                Spacer(Modifier.width(9.dp))
                Text("开始这轮练习", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: TimerMode, onSelect: (TimerMode) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(5.dp)) {
            listOf(
                TimerMode.COUNT_UP to "正计时",
                TimerMode.COUNT_DOWN to "倒计时"
            ).forEach { (mode, label) ->
                val isSelected = selected == mode
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(mode) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 13.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange((minutes - 5).coerceAtLeast(5)) }) {
            Icon(Icons.Default.Remove, "减少五分钟")
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                "$minutes 分钟",
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconButton(onClick = { onChange((minutes + 5).coerceAtMost(360)) }) {
            Icon(Icons.Default.Add, "增加五分钟")
        }
    }
}

@Composable
private fun ActiveTimerContent(
    snapshot: TimerStateStore.Snapshot,
    scaffoldPadding: PaddingValues,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSplit: () -> Boolean,
    onUndoSplit: () -> Unit,
    onSilence: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = scaffoldPadding.calculateTopPadding() + 26.dp,
                end = 24.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 28.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader(
            eyebrow = "In focus",
            title = "题刻",
            subtitle = ""
        )
        Spacer(Modifier.height(16.dp))
        Text(snapshot.subject, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        TimerDial(snapshot, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(20.dp))

        if (snapshot.hasReachedTarget) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "已超时 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "继续计时，完成后按实际用时保存",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (snapshot.plannedQuestionCount != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("本轮计划 ${snapshot.plannedQuestionCount} 题")
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        SegmentTimerControls(snapshot, onSplit, onUndoSplit, onPause, onResume, onFinish, onSilence)
        TextButton(
            onClick = { showDiscardDialog = true },
            modifier = Modifier.padding(top = 7.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("丢弃本次计时", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("丢弃这次计时？") },
            text = { Text("本次时长不会保存到记录和统计中。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDiscard()
                }) { Text("确认丢弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续计时") }
            }
        )
    }
}
