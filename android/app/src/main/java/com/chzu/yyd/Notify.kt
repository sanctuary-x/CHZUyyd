package com.chzu.yyd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notify {
    const val CHANNEL_RUN = "run"
    const val CHANNEL_RESULT = "result"
    const val RUN_ID = 100
    const val RESULT_ID = 101

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RUN, "跑步进行中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示跑步进度与剩余时间"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "跑步结果", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun contentIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildRun(ctx: Context, p: RunProgress?, text: String? = null): Notification {
        ensureChannels(ctx)
        val percent = p?.percent?.toInt() ?: 0
        val title = when (p?.stage) {
            "start" -> "正在准备跑步…"
            "uploaded" -> "轨迹已上传，正在结束…"
            "done" -> "跑步已完成"
            else -> "正在跑步（打表模式）"
        }
        val body = text ?: if (p != null && p.total > 1) {
            "已上传 ${p.sent}/${p.total} 个点 · 剩余 ${formatTime(p.remaining)}"
        } else "正在向服务器上传…"
        val builder = NotificationCompat.Builder(ctx, CHANNEL_RUN)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, percent, p == null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    fun updateRun(ctx: Context, p: RunProgress) {
        try {
            NotificationManagerCompat.from(ctx).notify(RUN_ID, buildRun(ctx, p))
        } catch (_: Exception) {
        }
    }

    fun result(ctx: Context, ok: Boolean, msg: String) {
        ensureChannels(ctx)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_RESULT)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(if (ok) "跑步完成" else "跑步失败")
            .setContentText(msg.take(120))
            .setContentIntent(contentIntent(ctx))
            .setAutoCancel(true)
        try {
            val nm = NotificationManagerCompat.from(ctx)
            if (nm.areNotificationsEnabled()) nm.notify(RESULT_ID, builder.build())
        } catch (_: Exception) {
        }
    }

    fun formatTime(seconds: Double): String {
        val s = seconds.toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
