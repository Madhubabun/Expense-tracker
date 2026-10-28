package app.expensetracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastTest {
    private val day10 = LocalDate.of(2026, 10, 10) // 22 days left including today

    @Test
    fun safeToSpendSharesWhatIsLeft() {
        // Budget 31,000. 8,000 spent before today, 500 today, 2,000 of bills still due.
        val o = Forecast.outlook(3_100_000, 850_000, 50_000, 200_000, 650_000, day10)
        assertEquals(22, o.daysLeft)
        assertEquals(3_100_000L - 800_000 - 200_000, o.availablePaise)
        assertEquals(o.availablePaise / 22, o.perDayPaise)
        assertEquals(o.perDayPaise - 50_000, o.safeTodayPaise)
    }

    @Test
    fun forecastAddsBillsAndDailyHabit() {
        val o = Forecast.outlook(2_500_000, 1_000_000, 0, 300_000, 700_000, day10)
        // habit = 700,000 / 10 = 70,000 a day for the 21 days after today
        assertEquals(1_000_000L + 300_000 + 70_000 * 21, o.projectedPaise)
        assertTrue(o.overBudget)
    }

    @Test
    fun unusualNeedsHistoryAndSize() {
        val usual = listOf(40_000L, 50_000, 60_000, 45_000, 55_000)
        assertEquals(50_000L, Anomaly.usual(300_000, usual))
        assertNull(Anomaly.usual(120_000, usual))
        assertNull(Anomaly.usual(300_000, listOf(50_000L, 60_000)))
    }

    @Test
    fun streakStopsAtASpendingDay() {
        val today = LocalDate.of(2026, 10, 10)
        val spend = setOf(today.minusDays(3))
        assertEquals(3, Streaks.noSpendStreak(spend, today, today.minusDays(30)))
        assertEquals(1, Streaks.noSpendStreak(emptySet(), today, today))
    }

    @Test
    fun scoreRewardsHabits() {
        val today = LocalDate.of(2026, 10, 10)
        val calm = Streaks.weeklyScore(emptyMap(), 100_000, 1f, today, today.minusDays(30))
        assertEquals(100, calm)
        val wild = (0..6).associate { today.minusDays(it.toLong()) to 500_000L }
        assertEquals(0, Streaks.weeklyScore(wild, 100_000, 0f, today, today.minusDays(30)))
    }
}
