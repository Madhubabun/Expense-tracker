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
fun PeriodScreen(state: AppState, period: Period, onEdit: (Txn) -> Unit) {
    val today = LocalDate.now()
    var offset by rememberSaveable(period) { mutableStateOf(0) }
    val anchor = Reports.shift(period, today, offset.toLong())
    val all = state.reportTxns()
    val summary = Reports.summarize(all, period, anchor)
    val previous = Reports.summarize(all, period, Reports.shift(period, anchor, -1))
    val change = Reports.percentChange(summary.spentPaise, previous.spentPaise)
    val compareLabel = when (period) { Period.WEEK -> "last week"; Period.MONTH -> "last month"; Period.YEAR -> "last year" }

    val title = when (period) {
        Period.WEEK -> if (offset == 0) "This week" else "Week"
        Period.MONTH -> anchor.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        Period.YEAR -> anchor.year.toString()
    }
    val range = when (period) {
        Period.YEAR -> "Jan – Dec"
        else -> "${summary.start.format(dayMonth)} – ${summary.end.format(dayMonth)} ${summary.end.year}"
    }
    val inRange = state.txns.filter { val d = LocalDate.ofEpochDay(it.epochDay); !d.isBefore(summary.start) && !d.isAfter(summary.end) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(range, title) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("‹") { offset-- }
                StepButton("›", enabled = offset < 0) { offset++ }
            }
        }

        if (summary.txnCount == 0) {
            val (emoji, head, text) = when (period) {
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

        SectionTitle("Where it went")
        if (period == Period.MONTH && summary.byCategory.isNotEmpty()) {
            Card { Donut(state, summary.byCategory, summary.spentPaise) }
        } else {
            CategoryPills(state, summary.byCategory)
        }

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
