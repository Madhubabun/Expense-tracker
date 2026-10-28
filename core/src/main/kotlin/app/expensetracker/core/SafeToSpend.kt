package app.expensetracker.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** How much is really free this month once the repeats are set aside. */
data class SafeToSpend(
    /** What came in this month plus what is still expected (a salary repeat), or the budget if neither is known. */
    val incomePaise: Long,
    /** Repeats (EMIs, loans, SIPs, rent, tithe) not paid yet this month. */
    val repeatsLeftPaise: Long,
    /** Spent so far this month, including money put into investments. */
    val spentPaise: Long,
    val leftPaise: Long,
    val perDayPaise: Long,
    val daysLeft: Int,
    val usingBudget: Boolean,
) {
    companion object {
        fun compute(
            receivedPaise: Long,
            expectedPaise: Long,
            budgetPaise: Long,
            repeatsLeftPaise: Long,
            spentPaise: Long,
            daysLeft: Int,
        ): SafeToSpend {
            val known = receivedPaise + expectedPaise
            val usingBudget = known <= 0
            val income = if (usingBudget) budgetPaise else known
            val left = income - repeatsLeftPaise - spentPaise
            val days = daysLeft.coerceAtLeast(1)
            return SafeToSpend(income, repeatsLeftPaise, spentPaise, left, left.coerceAtLeast(0) / days, days, usingBudget)
        }
    }
}

/** Dates and amounts for things that repeat every month. */
object RepeatPlan {
    /** Stands for "the last working day of the month" in a repeat's day of month. */
    const val LAST_WORKING_DAY = 99

    /** The day [dayOfMonth] falls on in [month]; short months use their last day. 99 means the last working day. */
    fun occurrence(month: YearMonth, dayOfMonth: Int): LocalDate =
        if (dayOfMonth >= LAST_WORKING_DAY) PayCycle.payday(month) else month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))

    /** The turn of this repeat closest to [day], if one falls within five days of it. */
    fun nearestOccurrence(day: LocalDate, dayOfMonth: Int): LocalDate? {
        val month = YearMonth.from(day)
        return listOf(month.minusMonths(1), month, month.plusMonths(1)).map { occurrence(it, dayOfMonth) }
            .minByOrNull { abs(it.toEpochDay() - day.toEpochDay()) }?.takeIf { abs(it.toEpochDay() - day.toEpochDay()) <= 5 }
    }

    /** Tithe-style repeats are a percentage of income; everything else is a fixed amount. */
    fun amount(fixedPaise: Long, percent: Int, incomePaise: Long): Long =
        if (percent > 0) incomePaise * percent / 100 else fixedPaise

    /**
     * Whether a bank debit or credit is this repeat arriving: close to its date (within five days, in this
     * or a neighbouring month) and the same amount (within 1%, or 25% for a percentage repeat).
     */
    fun matches(txnDay: LocalDate, txnPaise: Long, dayOfMonth: Int, expectedPaise: Long, percent: Int): Boolean {
        if (expectedPaise <= 0) return false
        val tolerance = if (percent > 0) expectedPaise / 4 else maxOf(100L, expectedPaise / 100)
        if (abs(txnPaise - expectedPaise) > tolerance) return false
        return nearestOccurrence(txnDay, dayOfMonth) != null
    }
}

/** Salary comes on the last working day of the month (weekends skipped), so money runs payday to payday. */
object PayCycle {
    /** The last Monday to Friday of [month]. */
    fun payday(month: YearMonth): LocalDate {
        var d = month.atEndOfMonth()
        while (d.dayOfWeek == java.time.DayOfWeek.SATURDAY || d.dayOfWeek == java.time.DayOfWeek.SUNDAY) d = d.minusDays(1)
        return d
    }

    /** One pay cycle: [start] is a payday, [end] the day before the next one, which is [nextPayday]. */
    data class Span(val start: LocalDate, val end: LocalDate, val nextPayday: LocalDate)

    fun span(today: LocalDate): Span {
        val month = YearMonth.from(today)
        val thisPay = payday(month)
        return if (!today.isBefore(thisPay)) {
            val next = payday(month.plusMonths(1))
            Span(thisPay, next.minusDays(1), next)
        } else {
            Span(payday(month.minusMonths(1)), thisPay.minusDays(1), thisPay)
        }
    }

    /** Whole days from [today] to the next payday, at least one. */
    fun daysLeft(today: LocalDate): Int = (span(today).nextPayday.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(1)
}
