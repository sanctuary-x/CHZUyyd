package com.chzu.yyd

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** 前台服务：真正执行一次打表跑步，通知栏显示进度，避免上传中被系统回收。 */
class RunService : Service() {
    companion object {
        private const val EXTRA_AUTO = "auto"
        private val running = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()

        fun start(ctx: Context, auto: Boolean) {
            val i = Intent(ctx, RunService::class.java).putExtra(EXTRA_AUTO, auto)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                AppRepo.log("启动跑步服务失败：${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        AppRepo.init(this)
        Notify.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val auto = intent?.getBooleanExtra(EXTRA_AUTO, false) ?: false
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY

        AppRepo.onRunStarted(auto)
        val notification = Notify.buildRun(this, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notify.RUN_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notify.RUN_ID, notification)
        }
        acquireWakeLock()

        val token = AppRepo.token()
        val device = AppRepo.deviceJson()
        val tasksDir = Store.tasksDir(this).absolutePath
        val drift = AppRepo.state.value.schedule.drift

        scope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    if (token.isBlank()) {
                        JSONObject().put("ok", false)
                            .put("msg", "账号尚未登录，请先在「账号」页登录。")
                            .put("need_login", true).put("need_cred", true)
                    } else {
                        Py.runTable(
                            token, device, tasksDir, drift,
                            ProgressSink(
                                onUpdate = { json -> onProgress(json) },
                                cancelled = { AppRepo.isCancelled() },
                            )
                        )
                    }
                } catch (e: Exception) {
                    JSONObject().put("ok", false)
                        .put("msg", "Python 调用失败：${e.message}")
                }
            }

            AppRepo.onRunFinished(auto, res)
            Notify.result(this@RunService, res.optBoolean("ok"), res.optString("msg"))
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            running.set(false)
            releaseWakeLock()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun onProgress(json: String) {
        AppRepo.onRunProgress(json)
        try {
            val o = JSONObject(json)
            val p = RunProgress(
                sent = o.optInt("sent"),
                total = o.optInt("total", 1),
                duration = o.optDouble("duration", 0.0),
                elapsed = o.optDouble("elapsed", 0.0),
                stage = o.optString("stage", "running"),
            )
            Notify.updateRun(this, p)
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CHZUyyd:run")
            wakeLock?.acquire(60 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        running.set(false)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
