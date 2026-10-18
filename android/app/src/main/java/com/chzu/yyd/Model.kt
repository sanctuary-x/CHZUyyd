package com.chzu.yyd

import org.json.JSONArray
import org.json.JSONObject

data class Account(
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val uuid: String = "",
    val sysEdition: String = "16",
    val loggedIn: Boolean = false,
    val lastError: String = "",
) {
    val hasToken: Boolean get() = token.isNotBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("username", username)
        put("password", password)
        put("token", token)
        put("device_id", deviceId)
        put("device_name", deviceName)
        put("uuid", uuid)
        put("sys_edition", sysEdition)
        put("logged_in", loggedIn)
        put("last_error", lastError)
    }

    companion object {
        fun fromJson(o: JSONObject?): Account {
            if (o == null) return Account()
            return Account(
                username = o.optString("username"),
                password = o.optString("password"),
                token = o.optString("token"),
                deviceId = o.optString("device_id"),
                deviceName = o.optString("device_name"),
                uuid = o.optString("uuid"),
                sysEdition = o.optString("sys_edition", "16"),
                loggedIn = o.optBoolean("logged_in", false),
                lastError = o.optString("last_error"),
            )
        }
    }
}

data class RunRecord(val at: String = "", val ok: Boolean = false, val msg: String = "")

data class Schedule(
    val targetCount: Int = 1,
    val doneCount: Int = 0,
    val times: List<String> = listOf("07:30"),
    val drift: Boolean = true,
    val enabled: Boolean = false,
    val catchupMinutes: Int = 30,
    val fired: Map<String, Boolean> = emptyMap(),
    val history: List<RunRecord> = emptyList(),
)

data class Settings(
    val notifications: Boolean = true,
)

data class AppState(
    val version: Int = 1,
    val account: Account = Account(),
    val schedule: Schedule = Schedule(),
    val settings: Settings = Settings(),
)

data class Term(val key: String, val value: String, val sjd: String) {
    val label: String get() = "$key（$sjd）"
}

data class RunItem(
    val id: String,
    val endTime: String,
    val mileage: String,
    val qualified: Boolean,
)

data class TrackItem(
    val name: String,
    val mileage: String,
    val duration: String,
    val points: Int,
)

data class RunProgress(
    val sent: Int = 0,
    val total: Int = 1,
    val duration: Double = 0.0,
    val elapsed: Double = 0.0,
    val stage: String = "start",
) {
    val percent: Float get() = if (total <= 0) 0f else (sent * 100f / total).coerceIn(0f, 100f)
    val remaining: Double get() = if (duration <= 0) 0.0 else (duration - elapsed).coerceAtLeast(0.0)
}

fun listToJsonArray(items: List<String>): JSONArray = JSONArray().apply { items.forEach { put(it) } }

fun jsonArrayToStrings(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
}

fun mapToJson(m: Map<String, Boolean>): JSONObject = JSONObject().apply {
    m.forEach { (k, v) -> put(k, v) }
}

fun jsonToMap(o: JSONObject?): Map<String, Boolean> {
    if (o == null) return emptyMap()
    val out = LinkedHashMap<String, Boolean>()
    o.keys().forEach { k -> out[k] = o.optBoolean(k) }
    return out
}
