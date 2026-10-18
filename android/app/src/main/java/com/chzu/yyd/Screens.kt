package com.chzu.yyd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// 1. 账号
// ---------------------------------------------------------------------------
@Composable
fun AccountScreen(
    account: Account,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var user by remember(account.username) { mutableStateOf(account.username) }
    var pwd by remember(account.password) { mutableStateOf(account.password) }
    var showPwd by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("学校（固定）") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("滁州学院", color = OkColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Pill("school_id = 106")
            }
            Spacer(Modifier.height(8.dp))
            KeyValue("服务器", "https://tiyu.chzu.edu.cn:8010/m-api")
            KeyValue("登录接口", "/login/appLoginCHZU")
            Hint("本程序仅供滁州学院使用，学校参数已内置，界面不提供学校选择。")
        }

        SectionCard("账号信息") {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("账号（学号）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (showPwd) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPwd = !showPwd }) {
                        Text(if (showPwd) "隐藏" else "显示", fontSize = 12.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSave(user, pwd) }, enabled = !busy) {
                    Text("保存账号")
                }
                Button(onClick = { onLogin(user, pwd) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(if (busy) 6.dp else 0.dp))
                    Text(if (account.hasToken) "重新登录" else "登录")
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE6ECF5))
            Spacer(Modifier.height(10.dp))
            val tokenText = when {
                account.loggedIn && account.hasToken -> "已登录（token：${account.token.take(10)}…）"
                account.hasToken -> "已有 token（未验证）"
                else -> "未登录"
            }
            KeyValue("登录状态", tokenText,
                if (account.loggedIn) OkColor else if (account.hasToken) WarnColor else Muted)
            KeyValue("设备名", account.deviceName.ifBlank { "(自动生成)" })
            if (account.lastError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Hint("最近一次问题：${account.lastError}", ErrColor)
            }
        }

        SectionCard("说明") {
            Hint("· 密码只保存在本机应用私有目录，请勿外传。")
            Hint("· 登录会使手机端云运动下线，请尽量减少重复登录次数。")
            Hint("· 若提示账号异常/登录失效，请在本页重新填写并登录。")
            Hint("· 本程序为技术交流学习用途，请遵守学校规定，风险自负。")
        }
    }
}

// ---------------------------------------------------------------------------
// 2. 历史数据
// ---------------------------------------------------------------------------
@Composable
fun HistoryScreen(
    terms: List<Term>,
    runs: List<RunItem>,
    tracks: List<TrackItem>,
    selectedTerm: Term?,
    selectedRun: RunItem?,
    busy: Boolean,
    onLoadTerms: () -> Unit,
    onSelectTerm: (Term) -> Unit,
    onSelectRun: (RunItem) -> Unit,
    onFetch: () -> Unit,
    onRefreshTracks: () -> Unit,
    onDeleteTrack: (String) -> Unit,
    onDeleteAllTracks: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showClearAll by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("第 1 步：选择学期") {
            OutlinedButton(onClick = onLoadTerms, enabled = !busy) {
                if (busy) CircularProgressIndicator(
                    Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp
                )
                Spacer(Modifier.width(if (busy) 6.dp else 0.dp))
                Text("获取学期列表")
            }
            Spacer(Modifier.height(8.dp))
            if (terms.isEmpty()) {
                Hint("还没有学期数据，点上面的按钮获取。")
            } else {
                terms.forEach { t ->
                    val selected = t.value == selectedTerm?.value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (selected) Color(0xFFDCEBFF) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectTerm(t) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.key, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(t.sjd, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        SectionCard("第 2 步：选择一条跑步记录") {
            if (runs.isEmpty()) {
                Hint("选择学期后会自动加载记录。")
            } else {
                runs.forEach { r ->
                    val selected = r.id == selectedRun?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (selected) Color(0xFFDCEBFF) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectRun(r) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.endTime, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("${r.mileage} km", fontSize = 13.sp, color = Navy)
                        if (r.qualified) {
                            Spacer(Modifier.width(6.dp))
                            Pill("有效", OkColor, Color(0xFFDDF3E6))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onFetch, enabled = !busy && selectedRun != null) {
                Text("抓取并保存到 tasks_CHZU")
            }
        }

        SectionCard("第 3 步：已保存的轨迹（${tracks.size}）") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Hint("打表模式每次随机选一条。")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showClearAll = true },
                        enabled = tracks.isNotEmpty() && !busy,
                    ) {
                        Text("清空全部", color = if (tracks.isEmpty()) Muted else ErrColor)
                    }
                    IconButton(onClick = onRefreshTracks) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
            if (tracks.isEmpty()) {
                Hint("tasks_CHZU 里还没有轨迹。本 App 不内置任何轨迹，请先抓取一条。", WarnColor)
            } else {
                tracks.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text("${t.mileage} km · ${t.points} 点", color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { pendingDelete = t.name }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrColor)
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val name = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除轨迹") },
            text = { Text("确定删除 $name 吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTrack(name)
                    pendingDelete = null
                }) { Text("删除", color = ErrColor) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("清空全部轨迹") },
            text = { Text("将删除 tasks_CHZU 中全部 ${tracks.size} 条轨迹，且不可恢复。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllTracks()
                    showClearAll = false
                }) { Text("全部删除", color = ErrColor) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 3. 定时跑步
// ---------------------------------------------------------------------------
@Composable
fun RunScreen(
    schedule: Schedule,
    tracks: List<TrackItem>,
    progress: RunProgress?,
    busy: Boolean,
    lastResult: Boolean?,
    lastMessage: String,
    onSave: (Int, List<String>, Boolean, Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRunNow: () -> Unit,
    onCancel: () -> Unit,
) {
    var target by remember(schedule.targetCount) { mutableStateOf(schedule.targetCount.toString()) }
    var times by remember(schedule.times) { mutableStateOf(schedule.times) }
    var drift by remember(schedule.drift) { mutableStateOf(schedule.drift) }
    var catchup by remember(schedule.catchupMinutes) { mutableStateOf(schedule.catchupMinutes) }
    var newTime by remember { mutableStateOf("") }
    var timeError by remember { mutableStateOf("") }
    var showStartConfirm by remember { mutableStateOf(false) }

    fun saveNow() {
        onSave(target.toIntOrNull() ?: 0, times, drift, catchup)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("跑步次数与时间") {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("目标跑步次数") },
                supportingText = { Text("0 = 不限次数，只按时间跑") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("每天执行时间（HH:MM）", fontSize = 13.sp, color = Muted)
            times.forEach { t ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { times = times - t }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrColor)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTime,
                    onValueChange = { newTime = it; timeError = "" },
                    label = { Text("HH:MM") },
                    singleLine = true,
                    modifier = Modifier.width(130.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    val t = ScheduleLogic.normalizeTime(newTime)
                    if (t == null) timeError = "时间格式应为 HH:MM"
                    else if (times.contains(t)) timeError = "该时间已存在"
                    else {
                        times = ScheduleLogic.normalizeTimes(times + t)
                        newTime = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加")
                }
            }
            if (timeError.isNotBlank()) Hint(timeError, ErrColor)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = drift, onCheckedChange = { drift = it })
                Spacer(Modifier.width(8.dp))
                Text("为轨迹添加漂移", fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("错过时间点后补跑", fontSize = 13.sp, color = Muted)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(2, 5, 15, 30, 60).forEach { m ->
                    FilterChip(
                        selected = catchup == m,
                        onClick = { catchup = m },
                        label = { Text("$m 分钟", fontSize = 12.sp) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { saveNow() }, enabled = !busy) { Text("保存设置") }
        }

        SectionCard("自动执行") {
            val next = ScheduleLogic.nextRun(LocalDateTime.now(), schedule.times)
            KeyValue("状态", if (schedule.enabled) "已开启" else "未开启",
                if (schedule.enabled) OkColor else Muted)
            KeyValue("已完成", if (schedule.targetCount > 0)
                "${schedule.doneCount} / ${schedule.targetCount} 次" else "${schedule.doneCount} 次（不限）")
            KeyValue(
                "下次执行",
                if (schedule.enabled && next != null)
                    next.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) else "—"
            )
            KeyValue("轨迹数量", "${tracks.size} 条")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    saveNow()
                    if ((target.toIntOrNull() ?: 0) == 0) showStartConfirm = true
                    else onStart()
                }, enabled = !busy) { Text("开始自动执行") }
                OutlinedButton(onClick = onStop, enabled = !busy) { Text("停止") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveNow(); onRunNow() }, enabled = !busy) {
                    Text("立即执行一次（不计次数）")
                }
                if (busy) {
                    OutlinedButton(onClick = onCancel) { Text("取消当前跑步", color = ErrColor) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Hint("需要保持本 App 运行；系统省电/后台限制可能影响定时，建议在「设置」页授权。")
        }

        SectionCard("跑步进度") {
            if (progress == null) {
                Text("当前没有跑步任务", color = Muted, fontSize = 14.sp)
                if (lastResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (lastResult == true) "上一次：跑步完成" else "上一次：跑步失败",
                        color = if (lastResult == true) OkColor else ErrColor,
                        fontSize = 13.sp,
                    )
                    if (lastMessage.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Hint(lastMessage, if (lastResult == true) OkColor else ErrColor)
                    }
                }
            } else {
                val title = when (progress.stage) {
                    "start" -> "正在准备跑步…"
                    "uploaded" -> "轨迹已上传，正在结束…"
                    else -> "正在跑步（打表模式）"
                }
                Text(title, color = Navy, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Blue,
                    trackColor = Color(0xFFDCEBFF),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${progress.percent.toInt()}% · 已上传 ${progress.sent} / ${progress.total} 个点",
                    fontSize = 13.sp,
                )
                Text(
                    "已用时 ${Notify.formatTime(progress.elapsed)}    预计剩余 ${Notify.formatTime(progress.remaining)}",
                    fontSize = 14.sp, color = Blue, fontWeight = FontWeight.Medium,
                )
            }
        }

        SectionCard("自动执行记录（最近 30 条）") {
            if (schedule.history.isEmpty()) {
                Hint("还没有自动执行记录。")
            } else {
                schedule.history.take(15).forEach { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(h.at, fontSize = 11.sp, color = Muted, modifier = Modifier.width(125.dp))
                        Pill(if (h.ok) "成功" else "失败",
                            if (h.ok) OkColor else ErrColor,
                            if (h.ok) Color(0xFFDDF3E6) else Color(0xFFFBE3E3))
                        Spacer(Modifier.width(8.dp))
                        Text(h.msg, fontSize = 11.sp, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showStartConfirm) {
        AlertDialog(
            onDismissRequest = { showStartConfirm = false },
            title = { Text("不限次数") },
            text = { Text("目标次数为 0（不限次数），每个时间点都会执行一次跑步，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirm = false
                    onStart()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirm = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 4. 运行日志
// ---------------------------------------------------------------------------
@Composable
fun LogsScreen(logs: List<String>, onClear: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("运行日志（${logs.size}）", fontWeight = FontWeight.SemiBold, color = Navy)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }
        }
        HorizontalDivider(color = Color(0xFFE6ECF5))
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无日志", color = Muted)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                items(logs) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. 设置
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreen(
    state: AppState,
    tracks: List<TrackItem>,
    onNotifications: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onReschedule: () -> Unit,
    onCheckCatchup: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    val exact = AlarmScheduler.canExact(ctx)
    val ignoringBattery = remember { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    var batteryState by remember { mutableStateOf(ignoringBattery) }

    fun openExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ctx.startActivity(Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${ctx.packageName}")
                ))
            } catch (e: Exception) {
                AppRepo.log("打开精确闹钟设置失败：${e.message}")
            }
        }
    }

    fun openBattery() {
        try {
            ctx.startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${ctx.packageName}")
            ))
            batteryState = true
        } catch (e: Exception) {
            AppRepo.log("打开电池优化设置失败：${e.message}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("学校") {
            KeyValue("学校", "滁州学院（固定）")
            KeyValue("school_id", "106")
            KeyValue("服务器", "https://tiyu.chzu.edu.cn:8010/m-api")
            KeyValue("登录接口", "/login/appLoginCHZU")
        }

        SectionCard("通知与后台") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("跑步通知", fontSize = 14.sp)
                    Hint("进度通知、完成/失败提醒")
                }
                Switch(
                    checked = state.settings.notifications,
                    onCheckedChange = {
                        onNotifications(it)
                        if (it) onRequestNotifications()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFE6ECF5))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("精确闹钟", fontSize = 14.sp)
                    Hint(if (exact) "已允许，可精确到点触发" else "未允许，定时可能延迟")
                }
                if (exact) Pill("已允许", OkColor, Color(0xFFDDF3E6))
                else OutlinedButton(onClick = { openExactAlarm() }) { Text("去授权") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("电池优化", fontSize = 14.sp)
                    Hint(if (batteryState) "已忽略电池优化" else "建议忽略，避免后台被杀")
                }
                if (batteryState) Pill("已忽略", OkColor, Color(0xFFDDF3E6))
                else OutlinedButton(onClick = { openBattery() }) { Text("去设置") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReschedule) { Text("重排闹钟") }
                OutlinedButton(onClick = onCheckCatchup) { Text("检查补跑") }
            }
            Spacer(Modifier.height(6.dp))
            Hint("国产 ROM 还需在系统设置中允许「自启动 / 后台活动」，否则锁屏后可能不执行。")
        }

        SectionCard("数据") {
            KeyValue("轨迹目录", Store.tasksDir(ctx).absolutePath)
            KeyValue("轨迹数量", "${tracks.size} 条")
            KeyValue("日志文件", Store.logFile(ctx).absolutePath)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClearLogs) { Text("清空日志") }
        }

        SectionCard("关于") {
            KeyValue("版本", "1.0.0")
            KeyValue("学校", "滁州学院（school_id=106）")
            Hint("本程序是 CHZUyyd 的 Android 版本：原生 Compose 界面 + Chaquopy 内嵌 Python 协议层。")
            Hint("本程序仅供滁州学院使用，不接受其他学校账号或轨迹。")
            Hint("登录会使手机端云运动下线；打表模式属于模拟数据，请遵守学校规定，风险自负。")
        }
    }
}

