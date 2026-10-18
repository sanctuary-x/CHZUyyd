package com.chzu.yyd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleLogicTest {
    private val now = LocalDateTime.of(2026, 10, 17, 7, 30, 30)

    @Test
    fun normalizeTime() {
        assertEquals("07:05", ScheduleLogic.normalizeTime("7:5"))
        assertEquals("23:59", ScheduleLogic.normalizeTime("23:59"))
        assertEquals("07:05", ScheduleLogic.normalizeTime("07：05"))
        assertNull(ScheduleLogic.normalizeTime("24:00"))
        assertNull(ScheduleLogic.normalizeTime("abc"))
    }

    @Test
    fun normalizeTimesSortsAndDedupes() {
        assertEquals(
            listOf("07:05", "18:00"),
            ScheduleLogic.normalizeTimes(listOf("18:00", "7:5", "07:05", "bad"))
        )
    }

    @Test
    fun dueSlotIsMatchedWithinWindow() {
        val due = ScheduleLogic.dueSlots(now, listOf("07:30", "18:00"), emptyMap(), 30)
        assertEquals(listOf("07:30"), due.map { it.hhmm })
        assertTrue(ScheduleLogic.dueSlots(
            now.withHour(7).withMinute(55), listOf("07:30"), emptyMap(), 30
        ).isNotEmpty())
        assertTrue(ScheduleLogic.dueSlots(
            now.withHour(8).withMinute(30), listOf("07:30"), emptyMap(), 30
        ).isEmpty())
    }

    @Test
    fun firedSlotIsNotDueAgain() {
        val key = ScheduleLogic.firedKey(now.toLocalDate(), "07:30")
        assertTrue(ScheduleLogic.dueSlots(
            now, listOf("07:30"), mapOf(key to true), 30
        ).isEmpty())
    }

    @Test
    fun nextRunTodayOrTomorrow() {
        assertEquals(
            LocalDateTime.of(2026, 10, 17, 18, 0),
            ScheduleLogic.nextRun(LocalDateTime.of(2026, 10, 17, 9, 0), listOf("07:30", "18:00"))
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 18, 7, 30),
            ScheduleLogic.nextRun(LocalDateTime.of(2026, 10, 17, 19, 0), listOf("07:30", "18:00"))
        )
    }

    @Test
    fun pruneFiredDropsOldEntries() {
        val today = LocalDate.of(2026, 10, 17)
        val kept = ScheduleLogic.firedKey(today, "07:30")
        val dropped = ScheduleLogic.firedKey(today.minusDays(30), "07:30")
        assertEquals(
            mapOf(kept to true),
            ScheduleLogic.pruneFired(mapOf(kept to true, dropped to true), today)
        )
    }

    @Test
    fun targetLogic() {
        assertEquals(2, ScheduleLogic.remaining(3, 1))
        assertNull(ScheduleLogic.remaining(0, 5))
        assertTrue(ScheduleLogic.reachTarget(3, 3))
        assertFalse(ScheduleLogic.reachTarget(0, 99))
        assertTrue(ScheduleLogic.reachTarget(1, 2))
    }
}
