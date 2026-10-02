package app.expensetracker.core

import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period { DAY, WEEK, MONTH, YEAR }

data class ReportTxn(
    val amountPaise: Long,
    val type: TxnType,
    val date: LocalDate,
    /** Empty string when the user has not picked a category yet. */
    val category: String,
    val kind: TxnKind = TxnKind.NORMAL,
) {
    /** Card bill payments and transfers between your own accounts are not spending or income. */
    val countable: Boolean
        get() = kind == TxnKind.NORMAL && category !in Categories.excludedFromTotals
}

data class Bar(val label: String, val spentPaise: Long)

data class CategoryTotal(val category: String, val spentPaise: Long)

data class Summary(
    val start: LocalDate,
    val end: LocalDate,
    val spentPaise: Long,
    val receivedPaise: Long,
    /** Spending per day (week), per week of the month (month) or per month (year). */
    val bars: List<Bar>,
    /** Spending per category, largest first. Uncategorized spending shows up as "Uncategorized". */
    val byCategory: List<CategoryTotal>,
    val txnCount: Int,
    /** Money put into investments (SIPs, mutual funds, stocks) in the range. Not counted as spending. */
    val investedPaise: Long = 0,
)

object Reports {
    const val UNCATEGORIZED_LABEL = "Uncategorized"

    /**
     * First and last day (inclusive) of the period holding [anchor].
     * WEEK is the 7 days ending on [anchor], so this week is always comparable with last week.
     */
    fun range(period: Period, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        Period.DAY -> anchor to anchor
        Period.WEEK -> anchor.minusDays(6) to anchor
        Period.MONTH -> anchor.withDayOfMonth(1).let { it to it.with(TemporalAdjusters.lastDayOfMonth()) }
        Period.YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
    }

    /** Change from [previous] to [current] in whole percent, or null when there is nothing to compare with. */
    fun percentChange(current: Long, previous: Long): Int? =
        if (previous <= 0) null else Math.round((current - previous) * 100.0 / previous).toInt()

    /** Moves [anchor] by [steps] periods (negative goes back). */
    fun shift(period: Period, anchor: LocalDate, steps: Long): LocalDate = when (period) {
        Period.DAY -> anchor.plusDays(steps)
        Period.WEEK -> anchor.plusDays(steps * 7)
        Period.MONTH -> anchor.plusMonths(steps)
        Period.YEAR -> anchor.plusYears(steps)
    }

    fun summarize(txns: List<ReportTxn>, period: Period, anchor: LocalDate): Summary {
        val (start, end) = range(period, anchor)
        val inRange = txns.filter { it.countable && !it.date.isBefore(start) && !it.date.isAfter(end) }
        val debits = inRange.filter { it.type == TxnType.DEBIT }

        val bars = when (period) {
            // A single day has no parts, so show it against the six days before it.
            Period.DAY -> (6 downTo 0).map { i ->
                val d = anchor.minusDays(i.toLong())
                val total = txns.filter { it.countable && it.type == TxnType.DEBIT && it.date == d }.sumOf { it.amountPaise }
                Bar(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), total)
            }
            Period.WEEK -> (0..6).map { i ->
                val d = start.plusDays(i.toLong())
                Bar(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), debits.filter { it.date == d }.sumOf { it.amountPaise })
            }
            // Weekly buckets: days 1-7, 8-14, 15-21, 22-28 and 29 to the end of the month.
            Period.MONTH -> (0..4).map { w ->
                Bar("W${w + 1}", debits.filter { (it.date.dayOfMonth - 1) / 7 == w }.sumOf { it.amountPaise })
            }
            Period.YEAR -> (1..12).map { m ->
                Bar(
                    java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    debits.filter { it.date.monthValue == m }.sumOf { it.amountPaise },
                )
            }
        }

        val byCategory = debits
            .groupBy { it.category.ifEmpty { UNCATEGORIZED_LABEL } }
            .map { (c, list) -> CategoryTotal(c, list.sumOf { it.amountPaise }) }
            .sortedByDescending { it.spentPaise }

        return Summary(
            start = start,
            end = end,
            spentPaise = debits.sumOf { it.amountPaise },
            receivedPaise = inRange.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise },
            bars = bars,
            byCategory = byCategory,
            txnCount = inRange.size,
            investedPaise = txns.filter {
                it.kind == TxnKind.NORMAL && it.category == Categories.INVEST && it.type == TxnType.DEBIT && !it.date.isBefore(start) && !it.date.isAfter(end)
            }.sumOf { it.amountPaise },
        )
    }

    /** "1,234.50" style text, using Indian digit grouping (12,34,567.00). */
    fun formatRupees(paise: Long): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val whole = (abs / 100).toString()
        val frac = (abs % 100).toString().padStart(2, '0')
        val grouped = if (whole.length <= 3) whole else {
            val last3 = whole.takeLast(3)
            val rest = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        return (if (negative) "-" else "") + "$grouped.$frac"
    }
}
