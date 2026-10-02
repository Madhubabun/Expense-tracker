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
            today: LocalDate,
        ): SafeToSpend {
            val known = receivedPaise + expectedPaise
            val usingBudget = known <= 0
            val income = if (usingBudget) budgetPaise else known
            val left = income - repeatsLeftPaise - spentPaise
            val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
            return SafeToSpend(income, repeatsLeftPaise, spentPaise, left, left.coerceAtLeast(0) / daysLeft, daysLeft, usingBudget)
        }
    }
}

/** Dates and amounts for things that repeat every month. */
object RepeatPlan {
    /** The day [dayOfMonth] falls on in [month]; short months use their last day. */
    fun occurrence(month: YearMonth, dayOfMonth: Int): LocalDate = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))

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
        val month = YearMonth.from(txnDay)
        return listOf(month.minusMonths(1), month, month.plusMonths(1)).any {
            abs(occurrence(it, dayOfMonth).toEpochDay() - txnDay.toEpochDay()) <= 5
        }
    }
}
