package com.chzu.yyd

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** 应用唯一状态仓库：Compose UI、前台服务、闹钟接收器共用。 */
object AppRepo {
    lateinit var ctx: Context
        private set

    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(Store.fresh())
    val state: StateFlow<AppState> = _state

    val logs = MutableStateFlow<List<String>>(emptyList())
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("就绪")
    val progress = MutableStateFlow<RunProgress?>(null)
    val lastResult = MutableStateFlow<Boolean?>(null)
    val lastMessage = MutableStateFlow("")

    val terms = MutableStateFlow<List<Term>>(emptyList())
    val runs = MutableStateFlow<List<RunItem>>(emptyList())
    val tracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val selectedTerm = MutableStateFlow<Term?>(null)
    val selectedRun = MutableStateFlow<RunItem?>(null)

    private val cancelFlag = AtomicBoolean(false)

    fun init(c: Context) {
        if (initialized) return
        initialized = true
        ctx = c.applicationContext
        try {
            Py.ensure(ctx)
        } catch (e: Exception) {
            log("Python 初始化失败：${e.message}")
        }
        Store.copyBundledTasks(ctx)
        _state.value = Store.load(ctx)
        Store.save(ctx, _state.value)
        syncStatus()
        refreshTracks()
        log("云运动 · 滁州学院助手 v1.0.0 已启动")
        log("学校：滁州学院（固定，school_id=106）")
        log("历史轨迹目录：${Store.tasksDir(ctx).absolutePath}")
    }

    // ------------------------------------------------------------ 基础
    fun deviceJson(): String {
        val a = _state.value.account
        return JSONObject().apply {
            put("device_id", a.deviceId)
            put("uuid", a.uuid)
            put("device_name", a.deviceName)
            put("sys_edition", a.sysEdition)
        }.toString()
    }

    fun token(): String = _state.value.account.token

    fun log(line: String) {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val text = "$ts $line"
        logs.value = (logs.value + text).takeLast(600)
        Store.appendLog(ctx, text)
    }

    private fun setState(persist: Boolean = true, transform: (AppState) -> AppState) {
        _state.value = transform(_state.value)
        if (persist) Store.save(ctx, _state.value)
    }

    private fun syncStatus() {
        val s = _state.value.schedule
        status.value = if (s.enabled) {
            "自动执行已开启" + if (ScheduleLogic.reachTarget(s.targetCount, s.doneCount))
                "（已达标）" else ""
        } else "就绪"
    }

    fun toast(msg: String) {
        status.value = msg
        log(msg)
    }

    // ------------------------------------------------------------ 账号
    fun saveAccount(username: String, password: String) {
        setState { st ->
            val changedUser = username.trim() != st.account.username
            st.copy(account = st.account.copy(
                username = username.trim(),
                password = password,
                token = if (changedUser) "" else st.account.token,
                loggedIn = if (changedUser) false else st.account.loggedIn,
                lastError = "",
            ))
        }
        toast("账号已保存")
    }

    fun login(username: String, password: String) {
        if (busy.value) {
            toast("已有任务正在执行，请稍候")
            return
        }
        if (username.isBlank() || password.isBlank()) {
            toast("账号和密码不能为空，请重新填写")
            return
        }
        saveAccount(username, password)
        scope.launch {
            busy.value = true
            status.value = "正在登录…"
            log("开始登录：${username.trim()}（滁州学院）")
            val res = withContext(Dispatchers.IO) {
                runCatching { Py.login(username.trim(), password, deviceJson()) }
                    .getOrElse { JSONObject().put("ok", false).put("msg", "Python 调用失败：${it.message}") }
            }
            busy.value = false
            if (res.optBoolean("ok")) {
                setState { st -> st.copy(account = st.account.copy(
                    token = res.optString("token"),
                    uuid = res.optString("uuid", st.account.uuid),
                    deviceId = res.optString("device_id", st.account.deviceId),
                    deviceName = res.optString("device_name", st.account.deviceName),
                    sysEdition = res.optString("sys_edition", st.account.sysEdition),
                    loggedIn = true,
                    lastError = "",
                )) }
                status.value = "登录成功"
                log("登录成功。注意：手机端云运动可能已下线，请尽量减少重复登录。")
            } else {
                val msg = res.optString("msg", "登录失败")
                setState { st -> st.copy(account = st.account.copy(
                    loggedIn = false, lastError = msg)) }
                status.value = "登录失败"
                log("登录失败：$msg")
            }
        }
    }

    fun handleAuthIssue(msg: String) {
        setState { st -> st.copy(account = st.account.copy(loggedIn = false, lastError = msg)) }
        log("账号异常：$msg")
        status.value = "账号异常，请重新登录"
    }

    // ------------------------------------------------------------ 历史
    fun loadTerms() {
        if (busy.value) return
        if (token().isBlank()) {
            toast("请先在「账号」页登录")
            return
        }
        scope.launch {
            busy.value = true
            status.value = "正在获取学期列表…"
            val res = withContext(Dispatchers.IO) {
                runCatching { Py.terms(token(), deviceJson()) }
                    .getOrElse { JSONObject().put("ok", false).put("msg", "Python 调用失败：${it.message}") }
            }
            busy.value = false
            if (!res.optBoolean("ok")) {
                fail(res, "获取学期列表")
                return@launch
            }
            val arr = res.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<Term>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(Term(o.optString("key"), o.optString("value"), o.optString("sjd")))
            }
            terms.value = list
            selectedTerm.value = list.firstOrNull()
            status.value = "共 ${list.size} 个学期"
            log("获取学期列表成功：${list.size} 个")
            list.firstOrNull()?.let { loadRuns(it) }
        }
    }

    fun loadRuns(term: Term) {
        selectedTerm.value = term
        if (busy.value) return
        scope.launch {
            busy.value = true
            status.value = "正在查询跑步记录…"
            val res = withContext(Dispatchers.IO) {
                runCatching { Py.runs(token(), deviceJson(), term.value) }
                    .getOrElse { JSONObject().put("ok", false).put("msg", "Python 调用失败：${it.message}") }
            }
            busy.value = false
            if (!res.optBoolean("ok")) {
                fail(res, "查询跑步记录")
                return@launch
            }
            val arr = res.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<RunItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(RunItem(
                    id = o.optString("id"),
                    endTime = o.optString("endTime"),
                    mileage = o.optString("recordMileage"),
                    qualified = o.optString("isQualified") == "1",
                ))
            }
            runs.value = list
            selectedRun.value = list.firstOrNull()
            status.value = "共 ${list.size} 条记录"
            log("查询跑步记录成功：${list.size} 条")
        }
    }

    fun fetchSelected() {
        val term = selectedTerm.value ?: run { toast("请先选择学期"); return }
        val run = selectedRun.value ?: run { toast("请先选择一条记录"); return }
        if (busy.value) return
        scope.launch {
            busy.value = true
            status.value = "正在抓取并保存轨迹…"
            val res = withContext(Dispatchers.IO) {
                runCatching { Py.fetch(token(), deviceJson(), term.value, run.id, Store.tasksDir(ctx).absolutePath) }
                    .getOrElse { JSONObject().put("ok", false).put("msg", "Python 调用失败：${it.message}") }
            }
            busy.value = false
            if (!res.optBoolean("ok")) {
                fail(res, "抓取轨迹")
                return@launch
            }
            val s = res.optJSONObject("summary") ?: JSONObject()
            status.value = "已保存 ${File(res.optString("saved_path")).name}"
            log("抓取完成：${res.optString("saved_path")}，距离 ${s.optString("recordMileage")} km")
            refreshTracks()
        }
    }

    fun refreshTracks() {
        Store.copyBundledTasks(ctx)
        val dir = Store.tasksDir(ctx)
        val list = mutableListOf<TrackItem>()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (!f.name.startsWith("tasklist_") || !f.name.endsWith(".json")) return@forEach
            try {
                val data = JSONObject(f.readText()).optJSONObject("data") ?: return@forEach
                list.add(TrackItem(
                    name = f.name,
                    mileage = data.optString("recordMileage", "0"),
                    duration = data.optString("duration", "0"),
                    points = data.optJSONArray("pointsList")?.length() ?: 0,
                ))
            } catch (_: Exception) {
            }
        }
        tracks.value = list
        if (list.isEmpty()) log("tasks_CHZU 暂无可用的历史轨迹。")
    }

    /** 删除单条已保存轨迹（只允许 tasks_CHZU 下的 tasklist_*.json）。 */
    fun deleteTrack(name: String) {
        if (busy.value || RunService.isRunning()) {
            toast("跑步任务执行中，暂不能删除轨迹")
            return
        }
        val dir = Store.tasksDir(ctx)
        val file = File(dir, name)
        val safe = name.startsWith("tasklist_") && name.endsWith(".json") &&
                !name.contains("/") && !name.contains("\\") &&
                file.canonicalFile.parentFile == dir.canonicalFile
        if (!safe) {
            toast("拒绝删除非法文件：$name")
            return
        }
        val ok = runCatching { file.delete() }.getOrDefault(false)
        if (ok) {
            log("已删除轨迹：$name")
            status.value = "已删除 $name"
        } else {
            log("删除失败：$name")
            status.value = "删除失败：$name"
        }
        refreshTracks()
    }

    /** 清空 tasks_CHZU 下所有已保存轨迹（说明.txt 保留）。 */
    fun deleteAllTracks() {
        if (busy.value || RunService.isRunning()) {
            toast("跑步任务执行中，暂不能清空轨迹")
            return
        }
        val dir = Store.tasksDir(ctx)
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("tasklist_") && f.name.endsWith(".json")) {
                if (runCatching { f.delete() }.getOrDefault(false)) count++
            }
        }
        log("已清空历史轨迹：共删除 $count 条")
        status.value = if (count > 0) "已清空 $count 条轨迹" else "没有可清空的轨迹"
        refreshTracks()
    }

    private fun fail(res: JSONObject, what: String) {
        val msg = res.optString("msg", "$what 失败")
        if (res.optBoolean("need_login") || res.optBoolean("need_cred")) {
            handleAuthIssue(msg)
        } else {
            status.value = "$what 失败"
            log("$what 失败：$msg")
        }
    }

    // ------------------------------------------------------------ 定时
    fun saveSchedule(target: Int, times: List<String>, drift: Boolean, catchup: Int) {
        val norm = ScheduleLogic.normalizeTimes(times)
        setState { st -> st.copy(schedule = st.schedule.copy(
            targetCount = target.coerceAtLeast(0),
            times = norm.ifEmpty { listOf("07:30") },
            drift = drift,
            catchupMinutes = catchup,
        )) }
        log("定时设置已保存：次数=$target，时间=${norm.joinToString(",")}，漂移=$drift")
        syncStatus()
    }

    fun startAuto() {
        val s = _state.value.schedule
        if (s.times.isEmpty()) {
            toast("请先添加至少一个执行时间")
            return
        }
        if (tracks.value.isEmpty()) {
            toast("还没有历史轨迹，请先在「历史」页抓取")
            return
        }
        setState { st -> st.copy(schedule = st.schedule.copy(enabled = true, fired = emptyMap())) }
        AlarmScheduler.reschedule(ctx, _state.value)
        status.value = "自动执行已开启"
        log("已开启自动执行：${s.times.joinToString(",")}，目标 ${s.targetCount} 次")
        checkCatchup()
    }

    fun stopAuto() {
        setState { st -> st.copy(schedule = st.schedule.copy(enabled = false)) }
        AlarmScheduler.cancel(ctx)
        status.value = "自动执行已停止"
        log("已停止自动执行")
    }

    fun markFired(date: LocalDate, hhmm: String) {
        setState { st ->
            val fired = ScheduleLogic.pruneFired(
                st.schedule.fired + (ScheduleLogic.firedKey(date, hhmm) to true)
            )
            st.copy(schedule = st.schedule.copy(fired = fired))
        }
    }

    fun checkCatchup(): Boolean {
        val s = _state.value.schedule
        if (!s.enabled) return false
        if (ScheduleLogic.reachTarget(s.targetCount, s.doneCount)) return false
        if (busy.value || RunService.isRunning()) return false
        val now = LocalDateTime.now()
        val due = ScheduleLogic.dueSlots(now, s.times, s.fired, s.catchupMinutes)
        val slot = due.firstOrNull() ?: return false
        markFired(now.toLocalDate(), slot.hhmm)
        log("补跑：命中 ${slot.hhmm}，开始自动执行")
        RunService.start(ctx, auto = true)
        return true
    }

    fun dueNow(): ScheduleLogic.Slot? {
        val s = _state.value.schedule
        if (!s.enabled) return null
        return ScheduleLogic.dueSlots(
            LocalDateTime.now(), s.times, s.fired, s.catchupMinutes
        ).firstOrNull()
    }

    // ------------------------------------------------------------ 跑步
    fun runNow() {
        if (busy.value || RunService.isRunning()) {
            toast("已有跑步任务正在执行")
            return
        }
        if (tracks.value.isEmpty()) {
            toast("还没有历史轨迹，请先在「历史」页抓取")
            return
        }
        RunService.start(ctx, auto = false)
    }

    fun onRunStarted(auto: Boolean) {
        busy.value = true
        lastResult.value = null
        lastMessage.value = ""
        progress.value = RunProgress(stage = "start")
        status.value = if (auto) "自动跑步执行中…" else "正在跑步…"
        log(if (auto) "开始自动跑步任务" else "开始手动跑步任务")
    }

    fun onRunProgress(json: String) {
        scope.launch {
            try {
                val o = JSONObject(json)
                progress.value = RunProgress(
                    sent = o.optInt("sent"),
                    total = o.optInt("total", 1),
                    duration = o.optDouble("duration", 0.0),
                    elapsed = o.optDouble("elapsed", 0.0),
                    stage = o.optString("stage", "running"),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun cancelRun() {
        cancelFlag.set(true)
        status.value = "正在取消…"
        log("用户请求取消当前跑步")
    }

    fun isCancelled(): Boolean = cancelFlag.get()

    fun onRunFinished(auto: Boolean, res: JSONObject) {
        busy.value = false
        cancelFlag.set(false)
        progress.value = null
        val ok = res.optBoolean("ok")
        lastResult.value = ok
        val msg = res.optString("msg", if (ok) "跑步完成" else "跑步失败")
        lastMessage.value = msg
        log((if (ok) "跑步完成：" else "跑步失败：") + msg)
        if (auto) {
            setState { st ->
                val rec = RunRecord(
                    at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()),
                    ok = ok, msg = msg,
                )
                var sch = st.schedule.copy(history = (listOf(rec) + st.schedule.history).take(30))
                if (ok) {
                    sch = sch.copy(doneCount = sch.doneCount + 1)
                    if (ScheduleLogic.reachTarget(sch.targetCount, sch.doneCount)) {
                        sch = sch.copy(enabled = false)
                        log("已达到目标跑步次数 ${sch.targetCount} 次，自动执行已停止。")
                    }
                }
                st.copy(schedule = sch)
            }
            AlarmScheduler.reschedule(ctx, _state.value)
        }
        status.value = if (ok) "跑步完成" else "跑步失败"
        if (!ok && (res.optBoolean("need_login") || res.optBoolean("need_cred"))) {
            handleAuthIssue(msg)
        }
        refreshTracks()
    }

    fun setNotifications(enabled: Boolean) {
        setState { st -> st.copy(settings = st.settings.copy(notifications = enabled)) }
    }

    fun clearLogs() {
        logs.value = emptyList()
        try {
            Store.logFile(ctx).delete()
        } catch (_: Exception) {
        }
    }
}
