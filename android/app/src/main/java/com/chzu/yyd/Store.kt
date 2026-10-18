package com.chzu.yyd

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Random

object Store {
    private const val PREFS = "chzuyyd"
    private const val KEY_STATE = "state"

    private val DEVICE_NAMES = listOf(
        "Xiaomi14", "Xiaomi15", "Redmi K70", "HUAWEI Mate 60",
        "OPPO Find X7", "vivo X100", "OnePlus 12", "Honor Magic6",
    )

    fun load(ctx: Context): AppState {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null) ?: return fresh()
        return try {
            val o = JSONObject(raw)
            val acc = Account.fromJson(o.optJSONObject("account"))
            val sch = fromJsonSchedule(o.optJSONObject("schedule"))
            val set = fromJsonSettings(o.optJSONObject("settings"))
            AppState(account = ensureDevice(acc), schedule = sch, settings = set)
        } catch (_: Exception) {
            fresh()
        }
    }

    fun save(ctx: Context, state: AppState) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, toJson(state).toString())
            .apply()
    }

    fun fresh(): AppState {
        return AppState(account = ensureDevice(Account()))
    }

    fun ensureDevice(acc: Account): Account {
        if (acc.deviceId.isNotBlank() && acc.uuid.isNotBlank()) return acc
        val r = Random()
        val id = (1000000000000000L + (r.nextDouble() * 8999999999999999L).toLong()).toString()
        return acc.copy(
            deviceId = acc.deviceId.ifBlank { id },
            uuid = acc.uuid.ifBlank { acc.deviceId.ifBlank { id } },
            deviceName = acc.deviceName.ifBlank { DEVICE_NAMES[r.nextInt(DEVICE_NAMES.size)] },
            sysEdition = acc.sysEdition.ifBlank { "16" },
        )
    }

    fun toJson(s: AppState): JSONObject = JSONObject().apply {
        put("version", 1)
        put("account", s.account.toJson())
        put("schedule", JSONObject().apply {
            put("target_count", s.schedule.targetCount)
            put("done_count", s.schedule.doneCount)
            put("times", JSONArray(s.schedule.times))
            put("drift", s.schedule.drift)
            put("enabled", s.schedule.enabled)
            put("catchup_minutes", s.schedule.catchupMinutes)
            put("fired", mapToJson(s.schedule.fired))
            put("history", JSONArray().apply {
                s.schedule.history.forEach {
                    put(JSONObject().apply {
                        put("at", it.at); put("ok", it.ok); put("msg", it.msg)
                    })
                }
            })
        })
        put("settings", JSONObject().apply {
            put("notifications", s.settings.notifications)
        })
    }

    private fun fromJsonSchedule(o: JSONObject?): Schedule {
        if (o == null) return Schedule()
        val hist = mutableListOf<RunRecord>()
        val arr = o.optJSONArray("history")
        if (arr != null) for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            hist.add(RunRecord(h.optString("at"), h.optBoolean("ok"), h.optString("msg")))
        }
        return Schedule(
            targetCount = o.optInt("target_count", 1),
            doneCount = o.optInt("done_count", 0),
            times = jsonArrayToStrings(o.optJSONArray("times")).ifEmpty { listOf("07:30") },
            drift = o.optBoolean("drift", true),
            enabled = o.optBoolean("enabled", false),
            catchupMinutes = o.optInt("catchup_minutes", 30),
            fired = jsonToMap(o.optJSONObject("fired")),
            history = hist.take(30),
        )
    }

    private fun fromJsonSettings(o: JSONObject?): Settings {
        if (o == null) return Settings()
        return Settings(notifications = o.optBoolean("notifications", true))
    }

    fun tasksDir(ctx: Context): File = File(ctx.filesDir, "tasks_CHZU")

    fun logsDir(ctx: Context): File = File(ctx.filesDir, "logs")

    fun logFile(ctx: Context): File = File(logsDir(ctx), "app.log")

    /** 首次启动时把安装包内置的 5 条滁州学院轨迹复制到私有目录。 */
    fun copyBundledTasks(ctx: Context) {
        val dir = tasksDir(ctx)
        dir.mkdirs()
        val existing = dir.listFiles()?.any {
            it.name.startsWith("tasklist_") && it.name.endsWith(".json")
        } ?: false
        if (existing) return
        try {
            val names = ctx.assets.list("tasks_CHZU") ?: return
            names.filter { it.endsWith(".json") || it == "说明.txt" }.forEach { name ->
                ctx.assets.open("tasks_CHZU/$name").use { input ->
                    File(dir, name).outputStream().use { out -> input.copyTo(out) }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun appendLog(ctx: Context, line: String) {
        try {
            logsDir(ctx).mkdirs()
            val f = logFile(ctx)
            if (f.length() > 1_000_000) f.delete()
            f.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }
}
