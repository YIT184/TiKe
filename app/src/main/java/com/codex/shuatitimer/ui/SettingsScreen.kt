package com.codex.shuatitimer.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.RingtoneManager
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.data.model.TimerUiStyle
import com.codex.shuatitimer.data.model.CountdownReminderMode
import com.codex.shuatitimer.data.preferences.FloatingCapsuleSettings
import com.codex.shuatitimer.data.preferences.CountdownReminderSettings

@Composable
fun SettingsScreen(viewModel: AppViewModel, scaffoldPadding: PaddingValues) {
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val capsuleSettings = remember(context) { FloatingCapsuleSettings(context) }
    var capsuleEnabled by remember { mutableStateOf(capsuleSettings.enabled) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showSync by rememberSaveable { mutableStateOf(false) }
    var chooseAlarmSound by rememberSaveable { mutableStateOf(false) }
    val audioFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::setAlarmSound)
    }
    val systemSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                androidx.core.content.IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                    ?.let(viewModel::setAlarmSound)
            }
        }
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        capsuleEnabled = overlayPermissionGranted
        capsuleSettings.enabled = overlayPermissionGranted
        viewModel.showMessage(if (overlayPermissionGranted) "悬浮计时胶囊已开启" else "未获得悬浮窗权限")
    }
    BackHandler(enabled = showSync) { showSync = false }

    if (showSync) {
        SyncScreen(viewModel, scaffoldPadding, onBack = { showSync = false })
        return
    }

    if (chooseAlarmSound) {
        AlertDialog(onDismissRequest = { chooseAlarmSound = false },
            icon = { Icon(Icons.Outlined.MusicNote, null) }, title = { Text("更换闹铃声音") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(setup.alarmSoundName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        chooseAlarmSound = false
                        try {
                            systemSoundLauncher.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹铃声音")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    CountdownReminderSettings(context).soundUri?.let(Uri::parse)
                                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            })
                        } catch (_: android.content.ActivityNotFoundException) { audioFileLauncher.launch(arrayOf("audio/*")) }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("选择系统铃声") }
                    OutlinedButton(onClick = { chooseAlarmSound = false; audioFileLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("从本地音频选择") }
                    TextButton(onClick = { viewModel.resetAlarmSound(); chooseAlarmSound = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("恢复系统默认闹铃")
                    }
                }
            }, confirmButton = { TextButton(onClick = { chooseAlarmSound = false }) { Text("取消") } })
    }

    ArtfulBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = scaffoldPadding.calculateTopPadding(),
                    bottom = scaffoldPadding.calculateBottomPadding()
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = 24.dp,
                    end = 20.dp,
                    bottom = 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader("Preferences", "设置", "按自己的习惯安排计时与同步。")

            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("01", "计时界面", "选择你喜欢的计时布局")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimerStyleChoice(
                            modifier = Modifier.weight(1f),
                            title = "经典布局",
                            description = "清晰、直接",
                            selected = setup.timerUiStyle == TimerUiStyle.CLASSIC,
                            onClick = { viewModel.setTimerUiStyle(TimerUiStyle.CLASSIC) }
                        )
                        TimerStyleChoice(
                            modifier = Modifier.weight(1f),
                            title = "环形布局",
                            description = "旋转圆环选择",
                            selected = setup.timerUiStyle == TimerUiStyle.RINGS,
                            onClick = { viewModel.setTimerUiStyle(TimerUiStyle.RINGS) }
                        )
                    }
                }
            }

            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("02", "计时辅助")
                    Spacer(Modifier.height(4.dp))
                    SettingSwitchRow(
                        title = "悬浮计时胶囊",
                        description = if (overlayPermissionGranted) "切换到其他应用时显示" else "开启时需要允许显示在其他应用上层",
                        checked = capsuleEnabled && overlayPermissionGranted,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                capsuleEnabled = false
                                capsuleSettings.enabled = false
                                viewModel.showMessage("悬浮计时胶囊已关闭")
                            } else if (Settings.canDrawOverlays(context)) {
                                overlayPermissionGranted = true
                                capsuleEnabled = true
                                capsuleSettings.enabled = true
                                viewModel.showMessage("悬浮计时胶囊已开启")
                            } else {
                                overlayPermissionLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }
                    )
                    SettingSwitchRow(
                        title = "计时时保持屏幕常亮",
                        description = "不影响锁屏后的后台计时",
                        checked = setup.keepScreenOn,
                        onCheckedChange = viewModel::setKeepScreenOn
                    )
                }
            }

            PaperCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle("03", "倒计时提醒方式", "到点时按所选方式提醒")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimerStyleChoice(
                            modifier = Modifier.weight(1f),
                            title = "消息弹窗",
                            description = "通知提示音",
                            selected = setup.countdownReminderMode == CountdownReminderMode.MESSAGE,
                            onClick = { viewModel.setCountdownReminderMode(CountdownReminderMode.MESSAGE) }
                        )
                        TimerStyleChoice(
                            modifier = Modifier.weight(1f),
                            title = "闹钟提醒",
                            description = "闹钟提示音",
                            selected = setup.countdownReminderMode == CountdownReminderMode.ALARM,
                            onClick = { viewModel.setCountdownReminderMode(CountdownReminderMode.ALARM) }
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !setup.importingAlarmSound) { chooseAlarmSound = true },
                        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("闹铃声音", style = MaterialTheme.typography.titleSmall)
                                Text(if (setup.importingAlarmSound) "正在保存音频…" else setup.alarmSoundName,
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.ChevronRight, "更换闹铃声音", Modifier.size(20.dp))
                        }
                    }
                    Text("到点后可在通知或计时页一键关闭铃声", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSync = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(9.dp)) {
                            androidx.compose.material3.Icon(
                                Icons.Outlined.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text("设备同步", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "搜索附近设备，或导入导出同步文件",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "打开设备同步",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerStyleChoice(
    modifier: Modifier,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 11.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
