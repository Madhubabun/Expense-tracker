package app.expensetracker.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

enum class Tone { SAVE, WARN, GOOD, INFO }

/** One plain-language observation about your spending. */
data class Insight(val emoji: String, val title: String, val text: String, val tone: Tone)

/** A category where trimming a little would free up money, with how much. */
data class SavingIdea(val category: String, val spentPaise: Long, val cutPercent: Int, val savePaise: Long)

data class InsightReport(val insights: List<Insight>, val ideas: List<SavingIdea>, val potentialPaise: Long)

/** Looks at the spending in a period and says what stands out and where you could save. Pure rules, nothing is sent anywhere. */
object Insights {
    /** Categories where most people can cut back without it hurting, and how much is a fair ask. */
    private val flexible = listOf("Food" to 15, "Shopping" to 20, "Entertainment" to 20, "Transport" to 10)
    private const val MIN_IDEA = 50_000L // ₹500
    private const val SMALL_SPEND = 20_000L // ₹200

    private fun money(paise: Long) = "₹" + Reports.formatRupees(paise).removeSuffix(".00")

    fun generate(
        txns: List<ReportTxn>,
        period: Period,
        anchor: LocalDate,
        budgetPaise: Long = 0,
        today: LocalDate = LocalDate.now(),
    ): InsightReport {
        val cur = Reports.summarize(txns, period, anchor)
        val prev = Reports.summarize(txns, period, Reports.shift(period, anchor, -1))
        val word = when (period) { Period.DAY -> "day"; Period.WEEK -> "week"; Period.MONTH -> "month"; Period.YEAR -> "year" }
        val perYear = when (period) { Period.DAY -> 365L; Period.WEEK -> 52L; Period.MONTH -> 12L; Period.YEAR -> 1L }
        val debits = txns.filter { it.countable && it.type == TxnType.DEBIT && !it.date.isBefore(cur.start) && !it.date.isAfter(cur.end) }
        val out = mutableListOf<Insight>()

        // Saving ideas
        val ideas = flexible.mapNotNull { (cat, pct) ->
            val spent = cur.byCategory.firstOrNull { it.category == cat }?.spentPaise ?: 0L
            if (spent >= MIN_IDEA) SavingIdea(cat, spent, pct, spent * pct / 100) else null
        }.sortedByDescending { it.savePaise }.take(3)
        val potential = ideas.sumOf { it.savePaise }

        // Month pace against the budget
        if (period == Period.MONTH && budgetPaise > 0 && YearMonthOf(anchor) == YearMonthOf(today) && today.dayOfMonth >= 3) {
            val projected = cur.spentPaise * anchor.lengthOfMonth() / today.dayOfMonth
            if (projected > budgetPaise * 105 / 100) {
                out += Insight("🚨", "Heading over budget", "At this pace you'll spend about ${money(projected)} by month end, ${money(projected - budgetPaise)} over your ${money(budgetPaise)} budget.", Tone.WARN)
            } else {
                out += Insight("✅", "On track for the month", "About ${money(projected)} by month end against your ${money(budgetPaise)} budget.", Tone.GOOD)
            }
        }

        // Overall change
        Reports.percentChange(cur.spentPaise, prev.spentPaise)?.let { pct ->
            if (pct <= -10) out += Insight("📉", "Spending is down $pct%".replace("-", ""), "You spent ${money(cur.spentPaise)} against ${money(prev.spentPaise)} last $word. Nice.", Tone.GOOD)
            else if (pct >= 15) out += Insight("📈", "Spending is up $pct%", "${money(cur.spentPaise)} against ${money(prev.spentPaise)} last $word.", Tone.WARN)
        }

        // Where the money goes
        val named = cur.byCategory.filter { it.category != Reports.UNCATEGORIZED_LABEL }
        named.firstOrNull()?.let { top ->
            val share = if (cur.spentPaise > 0) top.spentPaise * 100 / cur.spentPaise else 0
            if (share >= 1) out += Insight("🥇", "${top.category} takes the biggest share", "$share% of your spending, ${money(top.spentPaise)} this $word.", Tone.INFO)
        }

        // Categories that jumped
        cur.byCategory.mapNotNull { c ->
            val before = prev.byCategory.firstOrNull { it.category == c.category }?.spentPaise ?: 0L
            if (before > 0 && c.spentPaise > before * 125 / 100 && c.spentPaise - before >= MIN_IDEA) Triple(c.category, c.spentPaise, before) else null
        }.sortedByDescending { it.second - it.third }.take(2).forEach { (cat, now, before) ->
            val pct = (now - before) * 100 / before
            out += Insight("🔺", "$cat is up $pct%", "${money(now)} against ${money(before)} last $word. Back to that level would save ${money(now - before)}.", Tone.WARN)
        }

        // Small spends adding up
        debits.filter { it.amountPaise < SMALL_SPEND && it.category.isNotEmpty() }.groupBy { it.category }
            .filter { (_, l) -> l.size >= 8 && l.sumOf { it.amountPaise } >= 100_000 }
            .entries.sortedByDescending { e -> e.value.sumOf { it.amountPaise } }.take(1).forEach { (cat, l) ->
                out += Insight("🪙", "Small $cat spends add up", "${l.size} spends under ₹200 came to ${money(l.sumOf { it.amountPaise })}.", Tone.SAVE)
            }

        // Weekends against weekdays
        val last = minOf(cur.end, today)
        if (!last.isBefore(cur.start)) {
            val days = generateSequence(cur.start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
            val weekendDays = days.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            val weekdayDays = days.size - weekendDays
            if (weekendDays >= 2 && weekdayDays >= 2) {
                val weekend = debits.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }.sumOf { it.amountPaise } / weekendDays
                val weekday = debits.sumOf { it.amountPaise }.minus(weekend * weekendDays) / weekdayDays
                if (weekday > 0 && weekend > weekday * 3 / 2) {
                    out += Insight("🎉", "Weekends cost more", "You spend about ${money(weekend)} a weekend day against ${money(weekday)} on weekdays.", Tone.INFO)
                }
            }
        }

        // Biggest single spend
        debits.maxByOrNull { it.amountPaise }?.let { big ->
            out += Insight("💥", "Biggest spend: ${money(big.amountPaise)}", "On ${big.date}" + big.category.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty() + ".", Tone.INFO)
        }

        // Saved or overspent
        if (cur.receivedPaise > 0) {
            val net = cur.receivedPaise - cur.spentPaise
            val rate = net * 100 / cur.receivedPaise
            out += when {
                net < 0 -> Insight("⚠️", "You spent more than you received", "${money(-net)} more went out than came in this $word.", Tone.WARN)
                rate >= 20 -> Insight("💰", "You saved $rate% of your income", "${money(net)} kept this $word.", Tone.GOOD)
                else -> Insight("💼", "You kept $rate% of your income", "${money(net)} left over. Many people aim for 20%.", Tone.INFO)
            }
        }

        // Untagged spends
        val untagged = cur.byCategory.firstOrNull { it.category == Reports.UNCATEGORIZED_LABEL }?.spentPaise ?: 0L
        if (cur.spentPaise > 0 && untagged * 100 / cur.spentPaise >= 20) {
            out += Insight("🏷️", "${untagged * 100 / cur.spentPaise}% has no category", "Tag those spends and these tips get sharper.", Tone.INFO)
        }

        if (potential > 0) {
            out.add(0, Insight("💡", "You could save ${money(potential)} a $word", "That is about ${money(potential * perYear)} a year, by trimming just ${ideas.joinToString(" and ") { it.category }}.", Tone.SAVE))
        }
        return InsightReport(out.take(8), ideas, potential)
    }

    private fun YearMonthOf(d: LocalDate) = d.year * 100 + d.monthValue
}
