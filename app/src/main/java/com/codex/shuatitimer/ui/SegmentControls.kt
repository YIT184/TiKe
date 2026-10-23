package com.codex.shuatitimer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.shuatitimer.timer.TimerStateStore
import com.codex.shuatitimer.util.TimeUtils

@Composable
fun SegmentTimerControls(
    snapshot: TimerStateStore.Snapshot,
    onSplit: () -> Boolean,
    onUndoSplit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onSilence: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val boundaries = snapshot.segmentBoundaries
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (snapshot.alarmRinging) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("时间到，计时仍在继续", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = onSilence, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("silence_alarm")) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("关闭铃声")
                    }
                }
            }
        }
        PaperCard(modifier = Modifier.fillMaxWidth().animateContentSize(), contentPadding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Flag, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        AnimatedContent(boundaries.size, label = "segmentNumber") { count ->
                            Text("第 ${count + 1} 段", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(if (snapshot.running) "本段用时" else "本段已暂停", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(TimeUtils.formatDuration(snapshot.currentSegmentDurationMs), fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("current_segment_duration"))
                }
                if (boundaries.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val firstIndex = if (expanded) 0 else (boundaries.size - 3).coerceAtLeast(0)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 170.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(boundaries.drop(firstIndex)) { localIndex, boundary ->
                            val index = firstIndex + localIndex
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("第 ${index + 1} 段", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(TimeUtils.formatDuration(boundary.elapsedMs - (boundaries.getOrNull(index - 1)?.elapsedMs ?: 0L)),
                                    style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (boundaries.size > 3) TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "全部 ${boundaries.size} 段")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onUndoSplit, modifier = Modifier.testTag("undo_split"), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("撤销分段", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text("点击分段，结束后为每段选择模块", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = if (snapshot.running) onPause else onResume,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Icon(if (snapshot.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (snapshot.running) "暂停" else "继续")
            }
            OutlinedButton(onClick = { if (onSplit()) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                enabled = snapshot.canSplit, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("split_timer"),
                shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Outlined.Flag, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("分段")
            }
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(21.dp))
            Spacer(Modifier.width(8.dp))
            Text("完成并记录", style = MaterialTheme.typography.titleSmall)
        }
    }
}
