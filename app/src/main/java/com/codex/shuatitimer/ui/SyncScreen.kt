package com.codex.shuatitimer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.sync.DeviceIdentity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SyncScreen(viewModel: AppViewModel, padding: PaddingValues, onBack: () -> Unit) {
    val state by viewModel.sync.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var removing by remember { mutableStateOf<DeviceIdentity?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stopSync()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.stopSync() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::exportSync)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.prepareImport(uri) }
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri -> viewModel.prepareImport(uri, legacyCsv = true) }
    }
    val available = !state.busy && state.pendingImport == null
    ArtfulBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, padding.calculateTopPadding() + 24.dp, 20.dp, padding.calculateBottomPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    "Across devices",
                    "设备同步",
                    "让每一台设备，都记得你的进步。",
                    trailing = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置")
                        }
                    }
                )
            }
            item {
                PaperCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("本机", style = MaterialTheme.typography.labelMedium)
                                Text(state.deviceName, style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(enabled = available, onClick = { name = state.deviceName; editingName = true }) { Text("修改名称") }
                        }
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (state.searching || state.isLan) {
                            OutlinedButton(onClick = viewModel::stopSync) { Text(if (state.busy) "取消连接" else "停止搜索") }
                        } else {
                            Button(enabled = available, onClick = viewModel::startHosting) { Text("搜索附近设备") }
                        }
                        Text("两台设备连接同一 Wi-Fi，并在此页开启搜索。首次配对需对方同意，之后会自动记住。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.paired.isNotEmpty()) {
                item { Text("已配对设备", style = MaterialTheme.typography.titleLarge) }
                items(state.paired, key = { "paired-${it.id}" }) { paired ->
                    val online = state.nearby.firstOrNull { it.id == paired.id }
                    PaperCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(online?.name ?: paired.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (online == null) "等待设备上线" else "附近 · 可同步", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = available && online != null, onClick = { online?.let(viewModel::connectDevice) }) { Text("同步") }
                                TextButton(enabled = available, onClick = { removing = paired }) { Text("取消配对") }
                            }
                        }
                    }
                }
            }
            item { Text("附近设备", style = MaterialTheme.typography.titleLarge) }
            val unpaired = state.nearby.filter { nearby -> state.paired.none { it.id == nearby.id } }
            if (unpaired.isEmpty()) {
                item { Text(if (state.searching) "暂未发现新设备，请让另一台设备也开启搜索。" else "开启搜索后，附近的设备会显示在这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(unpaired, key = { "nearby-${it.id}" }) { device ->
                PaperCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("设备 ${device.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled = available, onClick = { viewModel.connectDevice(device) }) { Text("配对并同步") }
                    }
                }
            }
            item {
                PaperCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("同步文件", style = MaterialTheme.typography.titleLarge)
                        Text("不在同一网络时，可将文件发给另一台设备，再导入题刻。")
                        Button(enabled = available, modifier = Modifier.fillMaxWidth(), onClick = {
                            export.launch("题刻同步-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json")
                        }) { Text("导出同步文件") }
                        OutlinedButton(enabled = available, modifier = Modifier.fillMaxWidth(), onClick = {
                            importer.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                        }) { Text("导入并合并") }
                        TextButton(enabled = available, onClick = {
                            legacy.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                        }) { Text("导入旧版 CSV") }
                    }
                }
            }
        }
    }
    state.pairingRequest?.let { request ->
        AlertDialog(onDismissRequest = { viewModel.answerPairing(false) }, title = { Text("允许配对？") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${request.name} 希望与你配对并同步。")
                Text(request.verification, style = MaterialTheme.typography.headlineLarge)
                Text("确认两台设备显示的验证码一致后，再同意配对。")
            } },
            confirmButton = { TextButton(onClick = { viewModel.answerPairing(true) }) { Text("同意配对") } },
            dismissButton = { TextButton(onClick = { viewModel.answerPairing(false) }) { Text("拒绝") } })
    }
    state.waitingForPairing?.let { request ->
        AlertDialog(onDismissRequest = viewModel::stopSync, title = { Text("等待对方同意") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请在 ${request.name} 上确认配对。")
                Text(request.verification, style = MaterialTheme.typography.headlineLarge)
                Text("请核对两台设备上的验证码一致。")
            } }, confirmButton = { TextButton(onClick = viewModel::stopSync) { Text("取消") } })
    }
    state.pendingImport?.let { incoming ->
        AlertDialog(onDismissRequest = viewModel::cancelImport, title = { Text("导入记录？") },
            text = { Text("将合并 ${incoming.sessions.count { !it.deleted }} 条记录和 ${incoming.modules.count { !it.deleted }} 个模块。本机独有记录保留，较新的修改和删除也会同步。") },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("确认合并") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("取消") } })
    }
    if (editingName) AlertDialog(onDismissRequest = { editingName = false }, title = { Text("本机名称") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true, label = { Text("设备名称") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { viewModel.renameDevice(name); editingName = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { editingName = false }) { Text("取消") } })
    removing?.let { device ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("取消配对？") },
            text = { Text("取消与 ${device.name} 的配对后，下次连接需要重新确认。") },
            confirmButton = { TextButton(onClick = { viewModel.forgetDevice(device.id); removing = null }) { Text("取消配对") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("保留") } })
    }
}
