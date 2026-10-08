package com.codex.shuatitimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.StatisticsUiState
import com.codex.shuatitimer.SubjectStatistics
import com.codex.shuatitimer.ui.theme.Ochre
import com.codex.shuatitimer.ui.theme.Vermilion
import com.codex.shuatitimer.util.StatisticsCalculator
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Composable
fun StatisticsScreen(viewModel: AppViewModel, scaffoldPadding: PaddingValues) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()

    ArtfulBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 24.dp,
                end = 20.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Study insight",
                    title = "训练统计",
                    subtitle = "不追求漂亮数字，只观察真实的进步。"
                )
            }
            item { TodayHero(statistics) }
            item { CoreMetrics(statistics) }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.AutoGraph,
                                null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("最近 7 天", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "每天的有效刷题时长",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        WeeklyBars(statistics.daily)
                    }
                }
            }
            item {
                PaperCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Insights,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("分模块统计", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(18.dp))
                        if (statistics.subjects.isEmpty()) {
                            Text(
                                "完成计时后，这里会分别显示各模块的平均每题用时与正确率。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            SubjectBreakdown(statistics.subjects)
                        }
                    }
                }
            }
            item {
                Text(
                    "“稳定地完成，比偶尔地完美更重要。”",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TodayHero(stats: StatisticsUiState) {
    PaperCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今日专注",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    compactDuration(stats.overall.todayTotalDurationMs),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${stats.overall.todaySessionCount} 次完整练习",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Speed,
                        null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreMetrics(stats: StatisticsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard(
                label = "近 7 天",
                value = compactDuration(stats.overall.last7DaysTotalDurationMs),
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MiniStatCard(
                label = "累计练习",
                value = "${stats.overall.allTimeSessionCount} 次",
                accent = Vermilion,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard(
                label = "累计做题",
                value = "${stats.overall.totalQuestions} 题",
                accent = Ochre,
                modifier = Modifier.weight(1f)
            )
            MiniStatCard(
                label = "累计用时",
                value = compactDuration(stats.overall.allTimeTotalDurationMs),
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, accent: Color, modifier: Modifier) {
    PaperCard(modifier = modifier, contentPadding = 17.dp) {
        Column {
            Box(
                Modifier
                    .size(width = 28.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.height(13.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun WeeklyBars(days: List<StatisticsCalculator.DailySummary>) {
    val maxDuration = max(1L, days.maxOfOrNull { it.totalDurationMs } ?: 1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val fraction = (day.totalDurationMs.toFloat() / maxDuration).coerceIn(0f, 1f)
            val barColor = if (index == days.lastIndex) Vermilion
            else MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (day.totalDurationMs > 0L) shortMinutes(day.totalDurationMs) else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(104.dp)
                        .width(22.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (fraction == 0f) 0.03f else fraction.coerceAtLeast(0.08f))
                            .background(barColor, RoundedCornerShape(10.dp))
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    day.date.takeLast(5).replace('-', '/'),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubjectBreakdown(subjects: List<SubjectStatistics>) {
    val maxDuration = max(1L, subjects.maxOfOrNull { it.durationMs } ?: 1L)
    Column(verticalArrangement = Arrangement.spacedBy(19.dp)) {
        subjects.forEach { subject ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(subject.subject, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${subject.sessionCount} 次 · ${subject.questionCount} 题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        compactDuration(subject.durationMs),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("平均每题用时", style = MaterialTheme.typography.labelMedium)
                        Text(subject.avgSecondsPerQuestion?.let {
                            String.format(Locale.CHINA, "%.1f 秒", it)
                        } ?: "—", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("正确率", style = MaterialTheme.typography.labelMedium)
                        Text(subject.accuracy?.let {
                            String.format(Locale.CHINA, "%.1f%%", it * 100)
                        } ?: "—", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text("正确率按已填写答案结果的题数计算", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = subject.durationMs.toFloat() / maxDuration.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private fun compactDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0L -> if (minutes > 0L) "${hours}h ${minutes}m" else "${hours}h"
        minutes > 0L -> "$minutes 分钟"
        ms > 0L -> "< 1 分钟"
        else -> "0 分钟"
    }
}

private fun shortMinutes(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (minutes >= 60) {
        String.format(Locale.US, "%.1fh", minutes / 60.0)
    } else "${max(1L, minutes)}m"
}
