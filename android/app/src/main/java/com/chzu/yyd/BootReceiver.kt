package com.chzu.yyd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机 / 改时间 / 改时区 / 应用更新后恢复闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppRepo.init(context)
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AppRepo.log("收到系统广播：$action，恢复定时任务")
            AlarmScheduler.reschedule(context, AppRepo.state.value)
            AppRepo.checkCatchup()
        }
    }
}
