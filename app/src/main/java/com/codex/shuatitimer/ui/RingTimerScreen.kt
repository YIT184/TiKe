package com.codex.shuatitimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.codex.shuatitimer.TimerSetupUiState
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.ui.theme.Vermilion
import com.codex.shuatitimer.util.TimeUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val ModuleRingColor = Color(0xFF54B89A)
private val DurationRingColor = Color(0xFFE4AA4F)
private val ModeRingColor = Color(0xFF9E85E5)

private data class DurationSlot(val minutes: Int?, val label: String)

@Composable
fun RingTimerSetupContent(
    setup: TimerSetupUiState,
    scaffoldPadding: PaddingValues,
    onModeChange: (TimerMode) -> Unit,
    onSubjectChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onPlannedQuestionsChange: (Int?) -> Unit,
    onAddModule: (String) -> Unit,
    onDeleteModule: (String) -> Unit,
    onStart: () -> Unit
) {
    var showModules by remember { mutableStateOf(false) }
    if (showModules) {
        ModuleManager(setup.modules, onAddModule, onDeleteModule) { showModules = false }
    }
    var showCustomDuration by rememberSaveable { mutableStateOf(false) }
    if (showCustomDuration) {
        CustomDurationDialog(
            initialMinutes = setup.countdownMinutes,
            onConfirm = {
                onDurationChange(it)
                showCustomDuration = false
            },
            onDismiss = { showCustomDuration = false }
        )
    }
    val moduleCount = setup.modules.size
    val selectedModuleIndex = setup.modules.indexOf(setup.selectedSubject).coerceAtLeast(0)
    val modeIndex = if (setup.mode == TimerMode.COUNT_DOWN) 0 else 1
    val moduleStep = if (moduleCount > 0) (2f * PI.toFloat() / moduleCount) else 2f * PI.toFloat()
    val durationStep = PI.toFloat() / 10f
    val modeStep = PI.toFloat()
    var moduleRotation by remember { mutableFloatStateOf(-selectedModuleIndex * moduleStep) }
    var durationMinutes by remember { mutableIntStateOf(setup.countdownMinutes) }
    val durationRotation = -(durationMinutes - 5) / 5f * durationStep
    var modeRotation by remember { mutableFloatStateOf(-modeIndex * modeStep) }
    LaunchedEffect(setup.modules, setup.selectedSubject) {
        val index = setup.modules.indexOf(setup.selectedSubject).coerceAtLeast(0)
        moduleRotation = -index * (if (setup.modules.isNotEmpty()) 2f * PI.toFloat() / setup.modules.size else 2f * PI.toFloat())
    }
    LaunchedEffect(setup.countdownMinutes) {
        durationMinutes = setup.countdownMinutes
    }
    LaunchedEffect(setup.mode) {
        modeRotation = -(if (setup.mode == TimerMode.COUNT_DOWN) 0 else 1) * modeStep
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 18.dp,
                end = 16.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Circular focus", "题刻", "")
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
                .testTag("timer_rings"),
            contentAlignment = Alignment.Center
        ) {
            val radius = min(maxWidth.value, maxHeight.value) * 0.47f
            RingOrbit(
                modifier = Modifier.fillMaxSize(),
                moduleCount = moduleCount,
                mode = setup.mode,
                progress = if (setup.mode == TimerMode.COUNT_DOWN) setup.countdownMinutes / 360f else 0f,
                moduleRotation = moduleRotation,
                durationRotation = durationRotation
            )
            RingLabels(
                modules = setup.modules,
                moduleRotation = moduleRotation,
                durationMinutes = durationMinutes,
                showDuration = setup.mode == TimerMode.COUNT_DOWN,
                modeRotation = modeRotation,
                outerRadius = radius,
                moduleStep = moduleStep,
                modeStep = modeStep
            )
            RingInteractionOverlay(
                moduleCount = moduleCount,
                durationMinutes = durationMinutes,
                durationEnabled = setup.mode == TimerMode.COUNT_DOWN,
                moduleRotation = moduleRotation,
                modeRotation = modeRotation,
                onModuleRotationChange = { moduleRotation = it },
                onDurationChange = { durationMinutes = it },
                onModeRotationChange = { modeRotation = it },
                onModuleCommit = { index -> setup.modules.getOrNull(index)?.let(onSubjectChange) },
                onDurationCommit = onDurationChange,
                onDurationDoubleTap = { showCustomDuration = true },
                onModeCommit = { index -> onModeChange(if (index == 0) TimerMode.COUNT_DOWN else TimerMode.COUNT_UP) }
            )
            Button(
                onClick = onStart,
                enabled = setup.loaded && setup.modules.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(86.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(25.dp))
                    Text("开始", fontWeight = FontWeight.Bold)
                }
            }
        }
        TextButton(onClick = { showModules = true }) {
            Icon(Icons.Default.Add, null)
            Text("管理模块")
        }
        OutlinedTextField(
            value = setup.plannedQuestions?.toString().orEmpty(),
            onValueChange = { input ->
                onPlannedQuestionsChange(input.filter(Char::isDigit).take(4).toIntOrNull())
            },
            label = { Text("计划题数（选填）") },
            placeholder = { Text("例如：20") },
            suffix = { Text("题") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CustomDurationDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    val minutes = input.toIntOrNull()
    val valid = minutes != null && minutes in 5..360
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义倒计时") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text("时长") },
                suffix = { Text("分钟") },
                supportingText = { Text("输入 5–360 分钟，可设置任意整数分钟") },
                isError = input.isNotEmpty() && !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("custom_duration_input")
            )
        },
        confirmButton = {
            TextButton(onClick = { minutes?.takeIf { valid }?.let(onConfirm) }, enabled = valid) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RingOrbit(
    modifier: Modifier,
    moduleCount: Int,
    mode: TimerMode,
    progress: Float,
    active: TimerStateStore.Snapshot? = null,
    moduleRotation: Float = 0f,
    durationRotation: Float = 0f,
    durationCount: Int = 72
) {
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) * 0.48f
        val stroke = 8.dp.toPx()
        val moduleOuter = radius * 0.96f
        val moduleInner = radius * 0.71f
        val durationOuter = radius * 0.65f
        val durationInner = radius * 0.43f
        if (moduleCount > 0) {
            val sector = 360f / moduleCount
            repeat(moduleCount) { index ->
                val start = -90f + moduleRotation * 180f / PI.toFloat() + index * sector
                drawArc(
                    color = ModuleRingColor.copy(alpha = 0.055f),
                    startAngle = start,
                    sweepAngle = sector,
                    useCenter = false,
                    topLeft = Offset(center.x - (moduleOuter + moduleInner) / 2f, center.y - (moduleOuter + moduleInner) / 2f),
                    size = androidx.compose.ui.geometry.Size((moduleOuter + moduleInner), (moduleOuter + moduleInner)),
                    style = Stroke(moduleOuter - moduleInner)
                )
            }
            drawArc(
                color = ModuleRingColor.copy(alpha = 0.20f),
                startAngle = -90f - sector / 2f,
                sweepAngle = sector,
                useCenter = false,
                topLeft = Offset(center.x - (moduleOuter + moduleInner) / 2f, center.y - (moduleOuter + moduleInner) / 2f),
                size = androidx.compose.ui.geometry.Size((moduleOuter + moduleInner), (moduleOuter + moduleInner)),
                style = Stroke(moduleOuter - moduleInner)
            )
        }
        if (mode == TimerMode.COUNT_DOWN && durationCount > 0) {
            val sector = 360f / durationCount
            repeat(durationCount) { index ->
                val start = -90f + durationRotation * 180f / PI.toFloat() + index * sector
                drawArc(
                    color = DurationRingColor.copy(alpha = 0.035f),
                    startAngle = start,
                    sweepAngle = sector,
                    useCenter = false,
                    topLeft = Offset(center.x - (durationOuter + durationInner) / 2f, center.y - (durationOuter + durationInner) / 2f),
                    size = androidx.compose.ui.geometry.Size((durationOuter + durationInner), (durationOuter + durationInner)),
                    style = Stroke(durationOuter - durationInner)
                )
            }
            val highlightSweep = max(sector * 5f, 24f)
            drawArc(
                color = DurationRingColor.copy(alpha = 0.18f),
                startAngle = -90f - highlightSweep / 2f,
                sweepAngle = highlightSweep,
                useCenter = false,
                topLeft = Offset(center.x - (durationOuter + durationInner) / 2f, center.y - (durationOuter + durationInner) / 2f),
                size = androidx.compose.ui.geometry.Size((durationOuter + durationInner), (durationOuter + durationInner)),
                style = Stroke(durationOuter - durationInner)
            )
        }
        val rings = listOf(
            radius to ModuleRingColor,
            radius * 0.68f to DurationRingColor,
            radius * 0.40f to ModeRingColor
        )
        rings.forEachIndexed { index, (ringRadius, color) ->
            drawCircle(color.copy(alpha = 0.10f), ringRadius, center, style = Stroke(stroke))
            val sweep = when (index) {
                1 -> if (active != null) 360f * progress.coerceIn(0.04f, 1f) else 0f
                else -> 0f
            }
            val start = when (index) {
                0 -> -90f
                1 -> -90f
                else -> -90f
            }
            if (active != null && sweep > 0f) {
                drawArc(
                    color = (if (index == 1 && active.hasReachedTarget) Vermilion else color)
                        .copy(alpha = 0.9f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.RingLabels(
    modules: List<String>,
    moduleRotation: Float,
    durationMinutes: Int,
    showDuration: Boolean,
    modeRotation: Float,
    outerRadius: Float,
    moduleStep: Float,
    modeStep: Float
) {
    val moduleSelected = nearestRingIndex(moduleRotation, moduleStep, modules.size)
    if (modules.isNotEmpty()) {
        modules.forEachIndexed { index, module ->
            RingChoiceLabel(
                text = module,
                angle = -PI.toFloat() / 2f + moduleRotation + index * moduleStep,
                // Match the midpoint of the green annular sector drawn in RingOrbit.
                radius = outerRadius * 0.853f,
                selected = index == moduleSelected,
                color = ModuleRingColor,
                width = 92.dp
            )
        }
    }
    if (showDuration) {
        RingAnimatedDurationLabel(durationMinutes, outerRadius)
    } else {
        RingAnimatedDurationLabel(null, outerRadius)
    }
    listOf("倒计时", "正计时").forEachIndexed { index, text ->
        RingChoiceLabel(
            text = text,
            angle = -PI.toFloat() / 2f + modeRotation + index * modeStep,
            radius = outerRadius * 0.409f,
            selected = index == nearestRingIndex(modeRotation, modeStep, 2),
            color = ModeRingColor,
            width = 72.dp
        )
    }
}

@Composable
private fun BoxScope.RingAnimatedDurationLabel(minutes: Int?, outerRadius: Float) {
    val slot = DurationSlot(minutes, minutes?.let { "$it 分" } ?: "自由计时")
    var current by remember { mutableStateOf(slot) }
    var outgoing by remember { mutableStateOf<DurationSlot?>(null) }
    var direction by remember { mutableStateOf(1f) }
    val motion = remember { Animatable(1f) }
    LaunchedEffect(slot) {
        if (slot == current) return@LaunchedEffect
        outgoing = current
        val nextMinutes = slot.minutes
        val previousMinutes = current.minutes
        direction = when {
            nextMinutes == null || previousMinutes == null -> 1f
            nextMinutes >= previousMinutes -> 1f
            else -> -1f
        }
        current = slot
        motion.snapTo(0f)
        motion.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        if (current == slot) outgoing = null
    }
    val progress = motion.value
    val radius = outerRadius * 0.552f
    val top = -PI.toFloat() / 2f
    val arcSpan = PI.toFloat() / 7f
    outgoing?.let { old ->
        RingDurationArcText(
            slot = old,
            angle = top + direction * arcSpan * progress,
            radius = radius,
            alpha = 1f - progress,
            scale = 1f - 0.10f * progress
        )
    }
    RingDurationArcText(
        slot = current,
        angle = top - direction * arcSpan * (1f - progress),
        radius = radius,
        alpha = if (outgoing == null) 1f else 0.35f + 0.65f * progress,
        scale = 0.88f + 0.12f * progress
    )
}

@Composable
private fun BoxScope.RingDurationArcText(
    slot: DurationSlot,
    angle: Float,
    radius: Float,
    alpha: Float,
    scale: Float
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = (radius * cos(angle)).dp, y = (radius * sin(angle)).dp)
            .width(94.dp)
            .height(38.dp)
            .graphicsLayer {
                this.alpha = alpha.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            slot.label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = DurationRingColor,
            maxLines = 1
        )
    }
}

@Composable
private fun BoxScope.RingChoiceLabel(
    text: String,
    angle: Float,
    radius: Float,
    selected: Boolean,
    color: Color,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = (radius * cos(angle)).dp, y = (radius * sin(angle)).dp)
            .width(width)
            .height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontSize = if (selected) 16.sp else 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RingInteractionOverlay(
    moduleCount: Int,
    durationMinutes: Int,
    durationEnabled: Boolean,
    moduleRotation: Float,
    modeRotation: Float,
    onModuleRotationChange: (Float) -> Unit,
    onDurationChange: (Int) -> Unit,
    onModeRotationChange: (Float) -> Unit,
    onModuleCommit: (Int) -> Unit,
    onDurationCommit: (Int) -> Unit,
    onDurationDoubleTap: () -> Unit,
    onModeCommit: (Int) -> Unit
) {
    val latestModuleRotation = rememberUpdatedState(moduleRotation)
    val latestDurationMinutes = rememberUpdatedState(durationMinutes)
    val latestModeRotation = rememberUpdatedState(modeRotation)
    val latestOnModuleRotationChange = rememberUpdatedState(onModuleRotationChange)
    val latestOnDurationChange = rememberUpdatedState(onDurationChange)
    val latestOnModeRotationChange = rememberUpdatedState(onModeRotationChange)
    val latestOnModuleCommit = rememberUpdatedState(onModuleCommit)
    val latestOnDurationCommit = rememberUpdatedState(onDurationCommit)
    val latestOnDurationDoubleTap = rememberUpdatedState(onDurationDoubleTap)
    val latestOnModeCommit = rememberUpdatedState(onModeCommit)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(moduleCount, durationEnabled) {
                var lastDurationTapAt = 0L
                var lastDurationTapPosition = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val firstVector = down.position - center
                    val firstRadius = firstVector.getDistance()
                    val outer = min(size.width, size.height) * 0.47f
                    val touchedDurationText = durationEnabled &&
                        abs(firstVector.x) <= 50.dp.toPx() &&
                        abs(firstVector.y + outer * 0.552f) <= 19.dp.toPx()
                    val band = when {
                        touchedDurationText -> 1
                        firstRadius >= outer * 0.72f -> 0
                        firstRadius >= outer * 0.50f -> 1
                        firstRadius >= outer * 0.24f -> 2
                        else -> -1
                    }
                    var rotation = when (band) {
                        0 -> latestModuleRotation.value
                        2 -> latestModeRotation.value
                        else -> 0f
                    }
                    var selectedMinutes = latestDurationMinutes.value
                    var horizontalRemainder = 0f
                    val durationSwipeStepPx = 48.dp.toPx()
                    var moved = false
                    var releasedAt = 0L
                    var previousAngle = atan2(firstVector.y.toDouble(), firstVector.x.toDouble())
                    var previous = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            releasedAt = change.uptimeMillis
                            break
                        }
                        val delta = change.position - previous.position
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            moved = true
                        }
                        if (band >= 0 && (delta.getDistance() > 0.4f || band == 1 && delta.x != 0f)) {
                            if (band == 1) {
                                // The duration picker uses deliberate horizontal steps instead of
                                // measuring the finger's angle around the ring. This keeps a small
                                // accidental curve from skipping several 5-minute values.
                                if (durationEnabled) horizontalRemainder += delta.x
                                var stepped = false
                                while (horizontalRemainder >= durationSwipeStepPx) {
                                    if (selectedMinutes >= 360) {
                                        horizontalRemainder = 0f
                                        break
                                    }
                                    selectedMinutes = (selectedMinutes + 5).coerceAtMost(360)
                                    horizontalRemainder -= durationSwipeStepPx
                                    stepped = true
                                }
                                while (horizontalRemainder <= -durationSwipeStepPx) {
                                    if (selectedMinutes <= 5) {
                                        horizontalRemainder = 0f
                                        break
                                    }
                                    selectedMinutes = (selectedMinutes - 5).coerceAtLeast(5)
                                    horizontalRemainder += durationSwipeStepPx
                                    stepped = true
                                }
                                if (stepped) {
                                    moved = true
                                    latestOnDurationChange.value(selectedMinutes)
                                }
                                if (durationEnabled && abs(delta.x) > 0.4f) change.consume()
                            } else {
                                if (delta.getDistance() > 4f) moved = true
                                val vector = change.position - center
                                val angle = atan2(vector.y.toDouble(), vector.x.toDouble())
                                var angleDelta = (angle - previousAngle).toFloat()
                                if (angleDelta > PI) angleDelta -= (2 * PI).toFloat()
                                if (angleDelta < -PI) angleDelta += (2 * PI).toFloat()
                                rotation += angleDelta
                                when (band) {
                                    0 -> latestOnModuleRotationChange.value(rotation)
                                    2 -> latestOnModeRotationChange.value(rotation)
                                }
                                previousAngle = angle
                                change.consume()
                            }
                        }
                        previous = change
                    }
                    val tappedDuration = touchedDurationText && !moved &&
                        releasedAt > 0L &&
                        releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    if (tappedDuration) {
                        val tapInterval = down.uptimeMillis - lastDurationTapAt
                        if (lastDurationTapAt != 0L &&
                            tapInterval in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastDurationTapPosition).getDistance() <= 32.dp.toPx()
                        ) {
                            lastDurationTapAt = 0L
                            latestOnDurationDoubleTap.value()
                        } else {
                            lastDurationTapAt = releasedAt
                            lastDurationTapPosition = down.position
                        }
                    } else {
                        lastDurationTapAt = 0L
                    }
                    if (band >= 0) {
                        if (!moved) {
                            val clickVector = down.position - center
                            val clickAngle = atan2(clickVector.y.toDouble(), clickVector.x.toDouble()).toFloat()
                            when (band) {
                                0 -> {
                                    val step = if (moduleCount > 0) 2f * PI.toFloat() / moduleCount else 1f
                                    val index = nearestIndexAtAngle(clickAngle, rotation, step, moduleCount)
                                    rotation = -index * step
                                    latestOnModuleRotationChange.value(rotation)
                                    if (moduleCount > 0) latestOnModuleCommit.value(index)
                                }
                                1 -> Unit
                                2 -> {
                                    val index = nearestIndexAtAngle(clickAngle, rotation, PI.toFloat(), 2)
                                    rotation = -index * PI.toFloat()
                                    latestOnModeRotationChange.value(rotation)
                                    latestOnModeCommit.value(index)
                                }
                            }
                        } else when (band) {
                            0 -> {
                                val step = if (moduleCount > 0) 2f * PI.toFloat() / moduleCount else 1f
                                val index = nearestRingIndex(rotation, step, moduleCount)
                                latestOnModuleRotationChange.value(-index * step)
                                if (moduleCount > 0) latestOnModuleCommit.value(index)
                            }
                            1 -> {
                                if (durationEnabled) latestOnDurationCommit.value(selectedMinutes)
                            }
                            2 -> {
                                val index = nearestRingIndex(rotation, PI.toFloat(), 2)
                                latestOnModeRotationChange.value(-index * PI.toFloat())
                                latestOnModeCommit.value(index)
                            }
                        }
                    }
                }
            }
    )
}

private fun nearestRingIndex(rotation: Float, step: Float, count: Int): Int {
    if (count <= 0 || step <= 0f) return 0
    return ((-rotation / step).roundToInt()).mod(count)
}

private fun nearestIndexAtAngle(angle: Float, rotation: Float, step: Float, count: Int): Int {
    if (count <= 0 || step <= 0f) return 0
    var relative = angle - (-PI.toFloat() / 2f) - rotation
    while (relative > PI) relative -= (2 * PI).toFloat()
    while (relative < -PI) relative += (2 * PI).toFloat()
    return (relative / step).roundToInt().mod(count)
}

@Composable
fun RingActiveTimerContent(
    snapshot: TimerStateStore.Snapshot,
    scaffoldPadding: PaddingValues,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                top = scaffoldPadding.calculateTopPadding() + 22.dp,
                end = 18.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Circular focus", snapshot.subject, if (snapshot.running) "专注进行中" else "计时已暂停")
        Box(
            modifier = Modifier.fillMaxWidth().height(350.dp),
            contentAlignment = Alignment.Center
        ) {
            RingOrbit(
                modifier = Modifier.fillMaxSize(),
                moduleCount = 1,
                mode = snapshot.mode,
                progress = snapshot.progress,
                active = snapshot
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        snapshot.hasReachedTarget -> "已超时 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)}"
                        snapshot.mode == TimerMode.COUNT_DOWN -> "剩余时间"
                        else -> "实际用时"
                    },
                    color = if (snapshot.hasReachedTarget) Vermilion else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    TimeUtils.formatDuration(snapshot.displayDurationMs),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (snapshot.mode == TimerMode.COUNT_DOWN) "实际用时 ${TimeUtils.formatDuration(snapshot.effectiveElapsedMs)}" else "正计时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (snapshot.plannedQuestionCount != null) {
            Text("本轮计划 ${snapshot.plannedQuestionCount} 题", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = if (snapshot.running) onPause else onResume,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(if (snapshot.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (snapshot.running) "暂停" else "继续")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onFinish) {
            Icon(Icons.Outlined.StopCircle, null)
            Spacer(Modifier.width(6.dp))
            Text("完成并记录")
        }
        TextButton(onClick = { showDiscardDialog = true }) {
            Icon(Icons.Outlined.DeleteOutline, null)
            Spacer(Modifier.width(6.dp))
            Text("丢弃本次计时", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("丢弃这次计时？") },
            text = { Text("本次时长不会保存到记录和统计中。") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDiscard() }) {
                    Text("确认丢弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("继续计时") } }
        )
    }
}
