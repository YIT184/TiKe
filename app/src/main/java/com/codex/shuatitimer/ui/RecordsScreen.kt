package com.codex.shuatitimer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.TimerMode
import com.codex.shuatitimer.ui.theme.Vermilion
import com.codex.shuatitimer.util.TimeUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecordsScreen(viewModel: AppViewModel, scaffoldPadding: PaddingValues) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editSession by remember { mutableStateOf<Session?>(null) }
    var deleteSession by remember { mutableStateOf<Session?>(null) }
    var showClearAll by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(viewModel.exportCsv().toByteArray(Charsets.UTF_8))
                } ?: error("无法打开导出文件")
            }.onSuccess {
                viewModel.showMessage("CSV 已导出")
            }.onFailure {
                viewModel.showMessage("导出失败：${it.message ?: "未知错误"}")
            }
        }
    }

    ArtfulBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 24.dp,
                end = 20.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Practice log",
                    title = "练习记录",
                    subtitle = if (sessions.isEmpty()) "完成一次计时后，成果会出现在这里。"
                    else "共 ${sessions.size} 次练习，每一次都有迹可循。",
                    trailing = {
                        IconButton(
                            onClick = {
                                val stamp = LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
                                )
                                exportLauncher.launch("刷题记录_$stamp.csv")
                            }
                        ) {
                            Icon(Icons.Outlined.Download, "导出 CSV")
                        }
                        if (sessions.isNotEmpty()) {
                            IconButton(onClick = { showClearAll = true }) {
                                Icon(Icons.Outlined.DeleteSweep, "清空记录")
                            }
                        }
                    }
                )
            }

            if (sessions.isEmpty()) {
                item { EmptyRecords() }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onEdit = { editSession = session },
                        onDelete = { deleteSession = session }
                    )
                }
            }
        }
    }

    editSession?.let { session ->
        EditSessionDialog(
            session = session,
            onDismiss = { editSession = null },
            onSave = { questions, correct, note ->
                viewModel.updateSession(session, questions, correct, note).also { error ->
                    if (error == null) editSession = null
                }
            }
        )
    }

    deleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteSession = null },
            title = { Text("删除这条记录？") },
            text = { Text("${session.subject} · ${TimeUtils.formatDateTime(session.startWallClock)}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session)
                    deleteSession = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSession = null }) { Text("取消") }
            }
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("清空全部记录？") },
            text = { Text("这会删除所有刷题记录和统计数据，且无法恢复。建议先导出 CSV 备份。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllSessions()
                    showClearAll = false
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyRecords() {
    PaperCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.History,
                        null,
                        Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("第一笔记录，等你开始", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(7.dp))
            Text(
                "专注不必轰轰烈烈，\n从完整做完一道题开始。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    PaperCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TinyDot()
                        Spacer(Modifier.width(7.dp))
                        Text(session.subject, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑记录") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "删除记录")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        TimeUtils.formatDuration(session.effectiveElapsedMs),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        when {
                            session.mode == TimerMode.COUNT_DOWN.name && session.overtimeDurationMs > 0L ->
                                "限时训练 · 超时 ${TimeUtils.formatDuration(session.overtimeDurationMs)}"
                            session.mode == TimerMode.COUNT_DOWN.name -> "限时训练"
                            else -> "自由计时"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    session.segmentIndex?.let { index ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text("第 $index / ${session.segmentCount} 段", Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(5.dp))
                    }
                    Text(TimeUtils.formatDateTime(session.startWallClock), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricBlock("完成", session.questionCount?.let { "$it 题" } ?: "—")
                MetricBlock("正确", session.correctCount?.let { "$it 题" } ?: "—")
                MetricBlock(
                    "正确率",
                    session.accuracy?.let { String.format(Locale.CHINA, "%.0f%%", it * 100) } ?: "—"
                )
            }
            if (!session.note.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Notes,
                        null,
                        Modifier
                            .padding(top = 2.dp)
                            .size(17.dp),
                        tint = Vermilion
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        session.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSessionDialog(
    session: Session,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> String?
) {
    var questions by remember(session.id) {
        mutableStateOf(session.questionCount?.toString().orEmpty())
    }
    var correct by remember(session.id) {
        mutableStateOf(session.correctCount?.toString().orEmpty())
    }
    var note by remember(session.id) { mutableStateOf(session.note.orEmpty()) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑练习记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${session.subject} · ${TimeUtils.formatDuration(session.effectiveElapsedMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = questions,
                        onValueChange = { questions = it.filter(Char::isDigit).take(4) },
                        label = { Text("实际题数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = correct,
                        onValueChange = { correct = it.filter(Char::isDigit).take(4) },
                        label = { Text("正确题数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("复盘备注") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { error = onSave(questions, correct, note) }) {
                Text("保存修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
