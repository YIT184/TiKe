package com.codex.shuatitimer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModuleManager(
    names: List<String>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val duplicate = name.trim() in names
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理模块") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("添加后自动保存，下次可直接选择。删除模块会保留历史记录。")
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("新模块名称") }, singleLine = true,
                    isError = duplicate,
                    supportingText = { Text(if (duplicate) "这个模块已经存在" else "最多 20 个字，例如：图形推理") }
                )
                Button(
                    onClick = { onAdd(name.trim()); name = "" },
                    enabled = name.isNotBlank() && !duplicate && name.none(Char::isISOControl),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("添加并保存") }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(names, key = { it }) { module ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(module, Modifier.weight(1f))
                            IconButton(onClick = { deleting = module }) {
                                Icon(Icons.Default.DeleteOutline, "删除模块 $module")
                            }
                        }
                    }
                    if (names.isEmpty()) item { Text("还没有模块，请先添加一个。") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
    deleting?.let { module ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「$module」？") },
            text = { Text("该模块将从可选列表移除，历史练习及统计仍然保留。下次同步时会同步这一删除状态。") },
            confirmButton = {
                TextButton(onClick = { onDelete(module); deleting = null }) { Text("删除模块") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}
