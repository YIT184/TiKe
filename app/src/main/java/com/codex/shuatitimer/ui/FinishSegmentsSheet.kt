package com.codex.shuatitimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.shuatitimer.FinishDialogState
import com.codex.shuatitimer.timer.SegmentResult
import com.codex.shuatitimer.timer.segmentsAt
import com.codex.shuatitimer.util.TimeUtils

private val SegmentResultsSaver = listSaver<List<SegmentResult>, String>(
    save = { results -> results.flatMap { listOf(it.subject, it.questions, it.correct) } },
    restore = { values -> values.chunked(3).map { SegmentResult(it[0], it[1], it[2]) } }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishSegmentsSheet(
    state: FinishDialogState,
    modules: List<String>,
    onDismiss: () -> Unit,
    onCreateModule: (String, (String?) -> Unit) -> Unit,
    onSave: (List<SegmentResult>, String) -> String?
) {
    val segments = remember(state.finishWallClock) { state.snapshot.segmentsAt(state.finishWallClock) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var results by rememberSaveable(state.finishWallClock, stateSaver = SegmentResultsSaver) {
        mutableStateOf(segments.map {
            if (segments.size == 1) SegmentResult(state.snapshot.subject, state.snapshot.plannedQuestionCount?.toString().orEmpty())
            else SegmentResult()
        })
    }
    var note by rememberSaveable(state.finishWallClock) { mutableStateOf("") }
    var error by rememberSaveable(state.finishWallClock) { mutableStateOf<String?>(null) }
    var choosingModule by rememberSaveable(state.finishWallClock) { mutableStateOf<Int?>(null) }
    fun update(index: Int, value: SegmentResult) { results = results.toMutableList().apply { set(index, value) }; error = null }
    val assigned = results.count { it.subject in modules }
    ModalBottomSheet(
        onDismissRequest = { if (!state.saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding().testTag("finish_segments")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (segments.size > 1) "分段归档" else "记录本轮成果", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text("实际用时 ${TimeUtils.formatDuration(state.snapshot.effectiveElapsedMs)} · ${segments.size} 段",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, enabled = !state.saving) { Icon(Icons.Outlined.Close, "返回计时") }
            }
            val choosing = choosingModule
            if (choosing != null) {
                SegmentModulePanel(
                    segmentNumber = segments[choosing].number,
                    modules = modules,
                    selected = results[choosing].subject,
                    onSelect = { module ->
                        update(choosing, results[choosing].copy(subject = module))
                        choosingModule = null
                    },
                    onCreate = onCreateModule,
                    onDismiss = { choosingModule = null })
            } else {
                Spacer(Modifier.height(14.dp))
                if (segments.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("为每一段安排模块", style = MaterialTheme.typography.labelLarge)
                        Text("$assigned / ${segments.size} 已选择", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { assigned.toFloat() / segments.size },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.snapshot.overtimeDurationMs > 0) item {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("超时 ${TimeUtils.formatDuration(state.snapshot.overtimeDurationMs)}，已计入实际用时",
                                Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    itemsIndexed(segments, key = { _, segment -> segment.number }) { index, segment ->
                        val result = results[index]
                        PaperCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("第 ${segment.number.toString().padStart(2, '0')} 段", Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(TimeUtils.formatDuration(segment.durationMs), fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().testTag("segment_module_$index")
                                        .clickable(enabled = !state.saving) {
                                            focusManager.clearFocus(force = true)
                                            keyboard?.hide()
                                            choosingModule = index
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (result.subject in modules) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(result.subject.takeIf { it in modules } ?: "选择模块", Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
                                    }
                                }
                                if (index > 0 && results[index - 1].subject in modules && result.subject != results[index - 1].subject) {
                                    TextButton(onClick = { update(index, result.copy(subject = results[index - 1].subject)) },
                                        enabled = !state.saving, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Text("沿用上一段 · ${results[index - 1].subject}", maxLines = 2)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(result.questions,
                                        onValueChange = { update(index, result.copy(questions = it.filter(Char::isDigit).take(4))) },
                                        label = { Text("实际题数") }, placeholder = { Text("选填") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("segment_questions_$index"), enabled = !state.saving,
                                        shape = RoundedCornerShape(14.dp))
                                    OutlinedTextField(result.correct,
                                        onValueChange = { update(index, result.copy(correct = it.filter(Char::isDigit).take(4))) },
                                        label = { Text("正确题数") }, placeholder = { Text("选填") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).testTag("segment_correct_$index"), enabled = !state.saving,
                                        shape = RoundedCornerShape(14.dp))
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(note, onValueChange = { note = it.take(500) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("本轮备注（选填）") },
                            minLines = 2, maxLines = 4, enabled = !state.saving, shape = RoundedCornerShape(18.dp))
                        Text("未填题数的分段仅累计时长", Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (error ?: state.error)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = { error = onSave(results, note) }, enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("save_segments"),
                            shape = RoundedCornerShape(18.dp)) {
                            if (state.saving) CircularProgressIndicator(Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.saving) "正在保存" else if (segments.size > 1) "保存全部 ${segments.size} 段" else "保存记录")
                        }
                    }
                }
            }
        }
    }
}

/** Module chooser rendered inside the归档 sheet so no nested dialog window is needed. */
@Composable
private fun ColumnScope.SegmentModulePanel(
    segmentNumber: Int,
    modules: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onCreate: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by rememberSaveable(segmentNumber) { mutableStateOf(false) }
    var name by rememberSaveable(segmentNumber) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val duplicate = name.trim() in modules
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("第 $segmentNumber 段", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(if (creating) "新建模块" else "选择模块", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("返回") }
        }
        if (creating) {
            OutlinedTextField(name, onValueChange = { name = it.take(20); error = null },
                label = { Text("模块名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("new_segment_module"),
                enabled = !busy, shape = RoundedCornerShape(14.dp),
                isError = error != null || duplicate)
            Text(error ?: if (duplicate) "这个模块已存在，请返回选择" else "保存后可在所有计时和分段中使用",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null || duplicate) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { creating = false; name = ""; error = null }, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("返回选择")
                }
                Button(onClick = {
                    busy = true
                    val newName = name.trim()
                    onCreate(newName) { failure ->
                        busy = false
                        if (failure == null) onSelect(newName) else error = failure
                    }
                }, enabled = !busy && name.isNotBlank() && !duplicate && name.none(Char::isISOControl),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("confirm_new_segment_module"),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (busy) "正在保存" else "新建并使用")
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)) {
                items(modules) { module ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(module) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (module == selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(module, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(if (module == selected) Icons.Default.Check else Icons.Outlined.ChevronRight, null,
                                Modifier.size(20.dp),
                                tint = if (module == selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { creating = true; error = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("open_new_segment_module"),
                        shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建模块")
                    }
                }
            }
        }
    }
}
