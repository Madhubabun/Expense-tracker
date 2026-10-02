package app.expensetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Categories
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun Txn.countsTowardSpend() = kind == TxnKind.NORMAL && category !in Categories.excludedFromTotals && type == TxnType.DEBIT

/** Week, month and year views. Same layout, different range. */
@Composable
fun PeriodScreen(state: AppState, period: Period, offset: Int, onPeriod: (Period) -> Unit, onOffset: (Int) -> Unit, onInsights: (Period, LocalDate) -> Unit, onEdit: (Txn) -> Unit) {
    val today = LocalDate.now()
    var calendar by rememberSaveable { mutableStateOf(false) }
    val anchor = Reports.shift(period, today, offset.toLong())
    val all = state.reportTxns()
    val summary = Reports.summarize(all, period, anchor)
    val previous = Reports.summarize(all, period, Reports.shift(period, anchor, -1))
    val change = Reports.percentChange(summary.spentPaise, previous.spentPaise)
    val compareLabel = when (period) { Period.DAY -> "yesterday"; Period.WEEK -> "last week"; Period.MONTH -> "last month"; Period.YEAR -> "last year" }

    val inRange = state.txns.filter { val d = LocalDate.ofEpochDay(it.epochDay); !d.isBefore(summary.start) && !d.isAfter(summary.end) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Money out", "Expenses")
        PeriodBar(period, offset, onPeriod, onOffset)

        if (summary.txnCount == 0) {
            val (emoji, head, text) = when (period) {
                Period.DAY -> Triple("🫧", "Nothing spent this day", "Your wallet is chilling. Enjoy the quiet.")
                Period.WEEK -> Triple("🌤️", "Nothing this week", "Suspiciously disciplined. Your bank balance approves.")
                Period.MONTH -> Triple("🌱", "A clean slate", "No spends this month yet. Future you says thanks.")
                Period.YEAR -> Triple("🗓️", "No spends this year", "Bold move. Add one and we’ll start the story.")
            }
            EmptyState(emoji, head, text)
            return@Column
        }

        HeroCard {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("TOTAL SPENT", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, letterSpacing = 1.sp)
                        Text(rupees(summary.spentPaise).removeSuffix(".00"), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    }
                    if (change != null) {
                        Text(
                            (if (change <= 0) "↓ " else "↑ ") + Math.abs(change) + "% vs $compareLabel",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .22f)).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                val highlight = when (period) {
                    Period.DAY -> summary.bars.lastIndex
                    Period.WEEK -> summary.bars.lastIndex
                    Period.MONTH -> if (offset == 0) (today.dayOfMonth - 1) / 7 else summary.bars.lastIndex
                    Period.YEAR -> if (offset == 0) today.monthValue - 1 else 11
                }
                HeroBars(summary.bars, highlight)
                if (period == Period.MONTH) {
                    val budget = state.budgetPaise.coerceAtLeast(1)
                    val pct = (summary.spentPaise * 100f / budget)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("${rupees(summary.spentPaise)} of ${rupees(budget)}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${Math.round(pct)}% used", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f))) {
                        Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
        }

        IncomeVsSpending(summary.spentPaise, summary.receivedPaise)

        SectionTitle(when (period) { Period.DAY -> "Last 7 days"; Period.WEEK -> "Spending by day"; Period.MONTH -> "Spending by week"; Period.YEAR -> "Spending by month" })
        Card { SpendChart(summary.bars) }

        Card(Modifier.clickable { onInsights(period, anchor) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✨", fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Text("Insights and savings tips", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("See what stands out and where you could save", color = Pal.muted, fontSize = 12.sp)
                }
                Text("›", color = Pal.muted, fontSize = 22.sp)
            }
        }

        if (period == Period.MONTH) {
            ViewToggle(calendar) { calendar = it }
            if (calendar) {
                MonthCalendar(state, java.time.YearMonth.from(anchor), today, onEdit)
                return@Column
            }
        }

        SectionTitle("Where it went")
        if (period == Period.MONTH && summary.byCategory.isNotEmpty()) {
            Card { Donut(state, summary.byCategory, summary.spentPaise) }
        }
        Card { CategoryBars(state, summary.byCategory, summary.spentPaise) }

        if (period == Period.MONTH && offset == 0) MonthExtras(state, summary.byCategory, today)

        SectionTitle(if (period == Period.YEAR) "By month" else "Day by day")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (period == Period.YEAR) {
                val months = inRange.groupBy { LocalDate.ofEpochDay(it.epochDay).monthValue }.toSortedMap(reverseOrder())
                var first = true
                months.forEach { (m, list) ->
                    val spends = list.filter { it.countsTowardSpend() }.sortedByDescending { it.amountPaise }
                    FoldGroup(
                        id = "y${anchor.year}-$m",
                        title = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        sub = "${spends.size} spends · biggest first",
                        totalPaise = spends.sumOf { it.amountPaise },
                        startOpen = first,
                    ) { spends.take(4).forEach { TxnRow(state, it) { onEdit(it) } } }
                    first = false
                }
            } else {
                val days = inRange.groupBy { it.epochDay }.toSortedMap(reverseOrder())
                var index = 0
                days.forEach { (day, list) ->
                    val d = LocalDate.ofEpochDay(day)
                    FoldGroup(
                        id = "${period.name}-$day",
                        title = dayHeading(d, today),
                        sub = "${list.size} ${if (list.size == 1) "spend" else "spends"}",
                        totalPaise = list.filter { it.countsTowardSpend() }.sumOf { it.amountPaise },
                        startOpen = index < 2,
                    ) { list.forEach { TxnRow(state, it) { onEdit(it) } } }
                    index++
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Pal.surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Pal.fg else Pal.muted.copy(alpha = .4f), fontSize = 20.sp) }
}

/** Two bars: what came in against what went out, and what is left. */
@Composable
internal fun IncomeVsSpending(spent: Long, received: Long) {
    if (spent == 0L && received == 0L) return
    val top = maxOf(spent, received).coerceAtLeast(1).toFloat()
    val net = received - spent
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Income vs spending", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    (if (net >= 0) "Saved " else "Over by ") + rupees(Math.abs(net)).removeSuffix(".00"),
                    color = if (net >= 0) Pal.good else Pal.bad, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            listOf(Triple("In", received, Pal.good), Triple("Out", spent, Pal.bad)).forEach { (label, v, tint) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(label, color = Pal.muted, fontSize = 12.sp, modifier = Modifier.width(26.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Pal.surface2)) {
                        Box(Modifier.fillMaxWidth((v / top).coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(tint))
                    }
                    Text(rupees(v).removeSuffix(".00"), color = Pal.fg, fontSize = 12.sp)
                }
            }
        }
    }
}
