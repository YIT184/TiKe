package com.codex.shuatitimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.ui.theme.Vermilion
import com.codex.shuatitimer.util.TimeUtils
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArtfulBackground(content: @Composable BoxScope.() -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = primary.copy(alpha = 0.028f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 1.06f, size.height * 0.05f)
            )
            drawCircle(
                color = secondary.copy(alpha = 0.022f),
                radius = size.minDimension * 0.28f,
                center = Offset(-size.width * 0.03f, size.height * 0.72f)
            )
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(5.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(content = trailing)
    }
}

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
fun SectionTitle(index: String, title: String, helper: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    index,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TimerDial(snapshot: TimerStateStore.Snapshot, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val accent = if (snapshot.completed) Vermilion else MaterialTheme.colorScheme.secondary
    val dialProgress = when {
        snapshot.completed -> 1f
        snapshot.mode == TimerMode.COUNT_DOWN -> snapshot.progress
        else -> ((snapshot.effectiveElapsedMs % 60_000L) / 60_000f).coerceIn(0f, 1f)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.035f
            val radius = (size.minDimension - stroke) / 2f
            drawCircle(track.copy(alpha = 0.72f), radius, style = Stroke(stroke))
            drawArc(
                color = if (snapshot.completed) accent else primary,
                startAngle = -90f,
                sweepAngle = 360f * dialProgress,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            repeat(12) { index ->
                val angle = Math.toRadians((index * 30.0) - 90.0)
                val dotCenter = Offset(
                    center.x + cos(angle).toFloat() * radius * 0.86f,
                    center.y + sin(angle).toFloat() * radius * 0.86f
                )
                drawCircle(
                    color = primary.copy(alpha = if (index % 3 == 0) 0.36f else 0.16f),
                    radius = if (index % 3 == 0) 3.3f else 2.1f,
                    center = dotCenter
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    snapshot.completed -> "本轮完成"
                    snapshot.running && snapshot.mode == TimerMode.COUNT_DOWN && !snapshot.hasReachedTarget -> "剩余时间"
                    snapshot.mode == TimerMode.COUNT_DOWN && snapshot.hasReachedTarget -> "已超时"
                    snapshot.running -> "实际用时"
                    else -> "暂停中"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (snapshot.completed) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(7.dp))
            Text(
                TimeUtils.formatDuration(snapshot.displayDurationMs),
                fontFamily = FontFamily.Monospace,
                fontSize = 48.sp,
                lineHeight = 54.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-2).sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            if (snapshot.mode == TimerMode.COUNT_DOWN && snapshot.targetDurationMs != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    if (snapshot.hasReachedTarget) {
                        "目标 ${TimeUtils.formatDuration(snapshot.targetDurationMs)} · 超时 ${TimeUtils.formatDuration(snapshot.overtimeDurationMs)}"
                    } else {
                        "目标 ${TimeUtils.formatDuration(snapshot.targetDurationMs)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TinyDot(color: Color = MaterialTheme.colorScheme.secondary) {
    Box(
        Modifier
            .size(6.dp)
            .background(color, CircleShape)
    )
}
