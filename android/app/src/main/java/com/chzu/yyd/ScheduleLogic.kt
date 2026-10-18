package com.chzu.yyd

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 纯调度逻辑（不依赖 Android，可在 JVM 单元测试中直接测试）。 */
object ScheduleLogic {
    private val TIME_RE = Regex("^([01]?\\d|2[0-3]):([0-5]?\\d)$")
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun normalizeTime(text: String?): String? {
        if (text == null) return null
        val s = text.trim().replace("：", ":")
        val m = TIME_RE.matchEntire(s) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return "%02d:%02d".format(h, min)
    }

    fun normalizeTimes(items: List<String>?): List<String> {
        val out = LinkedHashSet<String>()
        items?.forEach { t -> normalizeTime(t)?.let { out.add(it) } }
        return out.sorted()
    }

    fun firedKey(date: LocalDate, hhmm: String): String = "${date.format(DATE_FMT)} $hhmm"

    private fun at(date: LocalDate, hhmm: String): LocalDateTime {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return LocalDateTime.of(date, LocalTime.of(h, m))
    }

    data class Slot(val hhmm: String, val at: LocalDateTime)

    fun dueSlots(
        now: LocalDateTime,
        times: List<String>,
        fired: Map<String, Boolean>,
        catchupMinutes: Int,
    ): List<Slot> {
        val window = catchupMinutes.coerceAtLeast(1)
        val out = mutableListOf<Slot>()
        for (hhmm in normalizeTimes(times)) {
            val scheduled = at(now.toLocalDate(), hhmm)
            if (!now.isBefore(scheduled) && now.isBefore(scheduled.plusMinutes(window.toLong()))) {
                if (fired[firedKey(now.toLocalDate(), hhmm)] != true) {
                    out.add(Slot(hhmm, scheduled))
                }
            }
        }
        return out.sortedBy { it.at }
    }

    fun nextRun(now: LocalDateTime, times: List<String>): LocalDateTime? {
        val ts = normalizeTimes(times)
        if (ts.isEmpty()) return null
        for (hhmm in ts) {
            val scheduled = at(now.toLocalDate(), hhmm)
            if (scheduled.isAfter(now)) return scheduled
        }
        return at(now.toLocalDate().plusDays(1), ts.first())
    }

    fun pruneFired(
        fired: Map<String, Boolean>,
        today: LocalDate = LocalDate.now(),
        keepDays: Long = 7,
    ): Map<String, Boolean> {
        val out = LinkedHashMap<String, Boolean>()
        fired.forEach { (k, v) ->
            try {
                val d = LocalDate.parse(k.substringBefore(" "), DATE_FMT)
                if (today.toEpochDay() - d.toEpochDay() <= keepDays) out[k] = v
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun remaining(target: Int, done: Int): Int? =
        if (target <= 0) null else (target - done).coerceAtLeast(0)

    fun reachTarget(target: Int, done: Int): Boolean =
        remaining(target, done) == 0
}
