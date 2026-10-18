package com.chzu.yyd

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/** Chaquopy 调用封装：所有 Python 函数返回 JSON 字符串。 */
object Py {
    @Synchronized
    fun ensure(ctx: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ctx.applicationContext))
        }
    }

    private fun module(): PyObject = Python.getInstance().getModule("proto.api")

    fun version(): JSONObject = JSONObject(module().callAttr("version").toString())

    fun school(): JSONObject = JSONObject(module().callAttr("school").toString())

    fun login(username: String, password: String, deviceJson: String): JSONObject =
        JSONObject(module().callAttr("login", username, password, deviceJson).toString())

    fun terms(token: String, deviceJson: String): JSONObject =
        JSONObject(module().callAttr("list_terms", token, deviceJson).toString())

    fun runs(token: String, deviceJson: String, termValue: String): JSONObject =
        JSONObject(module().callAttr("list_runs", token, deviceJson, termValue).toString())

    fun fetch(
        token: String, deviceJson: String, termValue: String, runId: String, tasksDir: String
    ): JSONObject = JSONObject(
        module().callAttr("fetch_save", token, deviceJson, termValue, runId, tasksDir).toString()
    )

    fun listTasks(tasksDir: String): JSONObject =
        JSONObject(module().callAttr("list_tasks", tasksDir).toString())

    fun runTable(
        token: String, deviceJson: String, tasksDir: String, drift: Boolean, sink: ProgressSink
    ): JSONObject = JSONObject(
        module().callAttr("run_table", token, deviceJson, tasksDir, drift, sink).toString()
    )
}

/** 传给 Python 的进度/取消回调。方法名必须与 proto/run.py、proto/api.py 中的调用一致。 */
class ProgressSink(
    private val onUpdate: (String) -> Unit,
    private val cancelled: () -> Boolean,
) {
    fun onProgress(json: String) = onUpdate(json)
    fun isCancelled(): Boolean = cancelled()
}
