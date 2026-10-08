package com.codex.shuatitimer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.shuatitimer.AppViewModel
import com.codex.shuatitimer.UiEvent
import kotlinx.coroutines.flow.collectLatest

private data class Destination(
    val label: String,
    val icon: ImageVector
)

@Composable
fun ShuatiTimerApp(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val finishDialog by viewModel.finishDialog.collectAsStateWithLifecycle()
    val destinations = remember {
        listOf(
            Destination("计时", Icons.Outlined.Timer),
            Destination("记录", Icons.Outlined.History),
            Destination("统计", Icons.Outlined.BarChart),
            Destination("设置", Icons.Outlined.Settings)
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                UiEvent.SessionSaved -> {
                    selectedTab = 1
                    snackbarHostState.showSnackbar("本次刷题记录已保存")
                }
            }
        }
    }

    LaunchedEffect(finishDialog.visible) {
        if (finishDialog.visible) selectedTab = 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label, color = if (selectedTab == index)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> TimerScreen(viewModel, padding)
            1 -> RecordsScreen(viewModel, padding)
            2 -> StatisticsScreen(viewModel, padding)
            else -> SettingsScreen(viewModel, padding)
        }
    }

}
