package com.chzu.yyd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

/** 精确闹钟到点：标记 fired 并启动前台服务执行跑步。 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppRepo.init(context)
        val state = AppRepo.state.value
        if (!state.schedule.enabled) return
        if (ScheduleLogic.reachTarget(state.schedule.targetCount, state.schedule.doneCount)) {
            AlarmScheduler.cancel(context)
            return
        }
        if (RunService.isRunning()) {
            AppRepo.log("闹钟触发时已有跑步任务，跳过本次并重排闹钟")
            AlarmScheduler.reschedule(context, state)
            return
        }
        val now = LocalDateTime.now()
        val due = ScheduleLogic.dueSlots(
            now, state.schedule.times, state.schedule.fired, state.schedule.catchupMinutes
        )
        val slot = due.firstOrNull()
        if (slot == null) {
            AppRepo.log("闹钟触发但不在补跑窗口内，重排下一次")
            AlarmScheduler.reschedule(context, state)
            return
        }
        AppRepo.markFired(now.toLocalDate(), slot.hhmm)
        AppRepo.log("闹钟触发：${slot.hhmm}，开始自动跑步")
        RunService.start(context, auto = true)
    }
}
