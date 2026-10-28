package app.expensetracker.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** One past debit, reduced to what bill detection needs. [key] groups spends at the same place. */
data class SpendPoint(val key: String, val label: String, val epochDay: Long, val amountPaise: Long)

data class RecurringBill(
    val label: String,
    val amountPaise: Long,
    val lastDay: Long,
    val nextDay: Long,
    val everyDays: Int,
)

/** Spots monthly bills (Netflix, rent, EMIs) from the spending history. */
object Recurring {

    /**
     * A bill is a merchant with at least two payments of a similar amount (within 15%), each about a month
     * after the one before (26 to 35 days), looking at the latest four payments. Bills whose next due day is
     * more than five days in the past are dropped: they have probably been cancelled.
     */
    fun detect(points: List<SpendPoint>, today: Long): List<RecurringBill> =
        points.groupBy { it.key }.mapNotNull { (_, all) ->
            if (all.size < 2) return@mapNotNull null
            val median = all.map { it.amountPaise }.sorted()[all.size / 2]
            val similar = all.filter { abs(it.amountPaise - median) <= median * 0.15 }
                .sortedBy { it.epochDay }.distinctBy { it.epochDay }.takeLast(4)
            if (similar.size < 2) return@mapNotNull null
            val gaps = similar.zipWithNext { a, b -> (b.epochDay - a.epochDay).toInt() }
            if (gaps.any { it !in 26..35 }) return@mapNotNull null
            val every = gaps.average().roundToInt()
            val last = similar.last()
            RecurringBill(last.label, median, last.epochDay, last.epochDay + every, every)
        }.filter { it.nextDay >= today - 5 }.sortedBy { it.nextDay }
}

object BudgetLevels {
    /** 100 once the budget is used up, 80 from four fifths of it, otherwise 0. */
    fun level(spentPaise: Long, budgetPaise: Long): Int = when {
        budgetPaise <= 0 -> 0
        spentPaise >= budgetPaise -> 100
        spentPaise * 5 >= budgetPaise * 4 -> 80
        else -> 0
    }
}
