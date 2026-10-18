package com.chzu.yyd

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppRepo.init(applicationContext)
        setContent {
            CHZUTheme { CHZUApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CHZUApp() {
    val state by AppRepo.state.collectAsState()
    val busy by AppRepo.busy.collectAsState()
    val status by AppRepo.status.collectAsState()
    val logs by AppRepo.logs.collectAsState()
    val progress by AppRepo.progress.collectAsState()
    val lastResult by AppRepo.lastResult.collectAsState()
    val lastMessage by AppRepo.lastMessage.collectAsState()
    val terms by AppRepo.terms.collectAsState()
    val runs by AppRepo.runs.collectAsState()
    val tracks by AppRepo.tracks.collectAsState()
    val selectedTerm by AppRepo.selectedTerm.collectAsState()
    val selectedRun by AppRepo.selectedRun.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AppRepo.checkCatchup()
    }

    val titles = listOf("账号", "历史数据", "定时跑步", "运行日志", "设置")

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Navy,
                ),
                title = {
                    Column {
                        Text(
                            "云运动 · 滁州学院助手",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(status, color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    Triple("账号", Icons.Default.Person, 0),
                    Triple("历史", Icons.Default.List, 1),
                    Triple("跑步", Icons.Default.PlayArrow, 2),
                    Triple("日志", Icons.Default.Info, 3),
                    Triple("设置", Icons.Default.Settings, 4),
                )
                items.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> AccountScreen(
                    account = state.account,
                    busy = busy,
                    onSave = { u, p -> AppRepo.saveAccount(u, p) },
                    onLogin = { u, p -> AppRepo.login(u, p) },
                )
                1 -> HistoryScreen(
                    terms = terms,
                    runs = runs,
                    tracks = tracks,
                    selectedTerm = selectedTerm,
                    selectedRun = selectedRun,
                    busy = busy,
                    onLoadTerms = { AppRepo.loadTerms() },
                    onSelectTerm = { AppRepo.loadRuns(it) },
                    onSelectRun = { AppRepo.selectedRun.value = it },
                    onFetch = { AppRepo.fetchSelected() },
                    onRefreshTracks = { AppRepo.refreshTracks() },
                    onDeleteTrack = { AppRepo.deleteTrack(it) },
                    onDeleteAllTracks = { AppRepo.deleteAllTracks() },
                )
                2 -> RunScreen(
                    schedule = state.schedule,
                    tracks = tracks,
                    progress = progress,
                    busy = busy,
                    lastResult = lastResult,
                    lastMessage = lastMessage,
                    onSave = { t, times, drift, catchup ->
                        AppRepo.saveSchedule(t, times, drift, catchup)
                    },
                    onStart = { AppRepo.startAuto() },
                    onStop = { AppRepo.stopAuto() },
                    onRunNow = { AppRepo.runNow() },
                    onCancel = { AppRepo.cancelRun() },
                )
                3 -> LogsScreen(logs = logs, onClear = { AppRepo.clearLogs() })
                4 -> SettingsScreen(
                    state = state,
                    tracks = tracks,
                    onNotifications = { AppRepo.setNotifications(it) },
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onReschedule = {
                        AlarmScheduler.reschedule(ctx, AppRepo.state.value)
                        AppRepo.toast("已重排闹钟")
                    },
                    onCheckCatchup = { AppRepo.checkCatchup() },
                    onClearLogs = { AppRepo.clearLogs() },
                )
            }
        }
    }
}
