package com.chzu.yyd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/** AlarmManager 精确闹钟注册（Doze 下用 setExactAndAllowWhileIdle）。 */
object AlarmScheduler {
    private const val REQUEST = 1001
    private const val ACTION = "com.chzu.yyd.ALARM"

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            ctx, REQUEST, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    fun reschedule(ctx: Context, state: AppState) {
        cancel(ctx)
        val s = state.schedule
        if (!s.enabled) return
        if (ScheduleLogic.reachTarget(s.targetCount, s.doneCount)) return
        val next = ScheduleLogic.nextRun(LocalDateTime.now(), s.times) ?: return
        val trigger = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                AppRepo.log("未授予精确闹钟权限，已退化为非精确闹钟")
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            AppRepo.log("下次自动执行：${next}（闹钟已注册）")
        } catch (e: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                AppRepo.log("精确闹钟被拒绝，已退化为非精确闹钟：${e.message}")
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            AppRepo.log("注册闹钟失败：${e.message}")
        }
    }

    fun cancel(ctx: Context) {
        try {
            ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))
        } catch (_: Exception) {
        }
    }
}
