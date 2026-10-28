package app.expensetracker.core

import java.time.LocalDate

/** How the rest of the month looks. All amounts in paise. */
data class Outlook(
    /** Budget left before today's spending, after setting aside bills still to come. Can be negative. */
    val availablePaise: Long,
    /** Fair amount to spend each remaining day, today included. */
    val perDayPaise: Long,
    val daysLeft: Int,
    /** What is still safe to spend today. Zero or less means you are at the limit. */
    val safeTodayPaise: Long,
    /** Where the month is likely to end. */
    val projectedPaise: Long,
    val overBudget: Boolean,
)

object Forecast {
    /**
     * [committedLaterPaise] is money that will leave before month end for bills and repeats not yet paid.
     * [variableSpentPaise] is this month's spending without those bills, used to guess the daily habit.
     */
    fun outlook(
        budgetPaise: Long,
        spentMonthPaise: Long,
        spentTodayPaise: Long,
        committedLaterPaise: Long,
        variableSpentPaise: Long,
        today: LocalDate,
    ): Outlook {
        val daysInMonth = today.lengthOfMonth()
        val daysLeft = daysInMonth - today.dayOfMonth + 1
        val available = budgetPaise - (spentMonthPaise - spentTodayPaise) - committedLaterPaise
        val perDay = if (daysLeft > 0) available / daysLeft else 0L
        val elapsed = today.dayOfMonth
        val habit = if (elapsed > 0) variableSpentPaise / elapsed else 0L
        val projected = spentMonthPaise + committedLaterPaise + habit * (daysLeft - 1)
        return Outlook(available, perDay, daysLeft, perDay - spentTodayPaise, projected, budgetPaise > 0 && projected > budgetPaise)
    }
}

/** Spots a spend that is far bigger than usual. */
object Anomaly {
    /**
     * Returns the usual amount when [amountPaise] is more than 3 times the median of [history] and at least
     * [minPaise], else null. Needs at least [minHistory] earlier spends to judge.
     */
    fun usual(amountPaise: Long, history: List<Long>, minPaise: Long = 100_000, minHistory: Int = 4): Long? {
        if (history.size < minHistory || amountPaise < minPaise) return null
        val median = history.sorted()[history.size / 2]
        return if (median > 0 && amountPaise > median * 3) median else null
    }
}

/** Little rewards for good habits. */
object Streaks {
    /** Days in a row, counting back from [today], with no spending. Days before [start] don't count. */
    fun noSpendStreak(spendDays: Set<LocalDate>, today: LocalDate, start: LocalDate): Int {
        var n = 0
        var d = today
        while (!d.isBefore(start) && d !in spendDays) { n++; d = d.minusDays(1) }
        return n
    }

    /**
     * 0 to 100 for the last 7 days: days within the daily budget (60 points), how much is categorised (20)
     * and no-spend days, up to two (20).
     */
    fun weeklyScore(dailySpend: Map<LocalDate, Long>, dailyBudgetPaise: Long, taggedFraction: Float, today: LocalDate, start: LocalDate): Int {
        val days = (0..6).map { today.minusDays(it.toLong()) }.filter { !it.isBefore(start) }
        if (days.isEmpty()) return 0
        val under = days.count { (dailySpend[it] ?: 0L) <= dailyBudgetPaise }
        val none = days.count { (dailySpend[it] ?: 0L) == 0L }
        val raw = 60.0 * under / days.size + 20.0 * taggedFraction.coerceIn(0f, 1f) + 20.0 * minOf(none, 2) / 2
        return Math.round(raw).toInt().coerceIn(0, 100)
    }
}
