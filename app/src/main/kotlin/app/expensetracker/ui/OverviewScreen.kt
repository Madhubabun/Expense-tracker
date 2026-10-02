package app.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.RepeatDue
import app.expensetracker.data.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun short(paise: Long) = rupees(paise).removeSuffix(".00")

/** Where you stand: income, spending and investing for the chosen period, what is safe to spend, and what is due this week. */
@Composable
fun OverviewScreen(state: AppState, period: Period, offset: Int, onPeriod: (Period) -> Unit, onOffset: (Int) -> Unit, onEdit: (Txn) -> Unit) {
    val today = LocalDate.now()
    val anchor = Reports.shift(period, today, offset.toLong())
    val summary = Reports.summarize(state.reportTxns(), period, anchor)
    val safe = state.safe
    val need = state.needsCategory
    var editIncome by remember { mutableStateOf(false) }
    val inRange = state.txns.filter { val d = LocalDate.ofEpochDay(it.epochDay); !d.isBefore(summary.start) && !d.isAfter(summary.end) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(today.format(DateTimeFormatter.ofPattern("EEEE · d MMM yyyy", Locale.ENGLISH)), "Overview") {
            if (need.isNotEmpty()) Pill("${need.size} need a category", onClick = { onEdit(need.first()) })
        }
        PeriodBar(period, offset, onPeriod, onOffset)

        HeroCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("INCOME" to summary.receivedPaise, "EXPENSES" to summary.spentPaise, "INVESTED" to summary.investedPaise).forEach { (label, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Color.White.copy(alpha = .78f), fontSize = 10.sp, letterSpacing = 1.sp)
                        Text(short(v), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                    }
                }
            }
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SAFE TO SPEND UNTIL PAYDAY · ${state.payday.format(shortDay).uppercase()}", color = Pal.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Left in total", color = Pal.muted, fontSize = 12.sp)
                        Text(signedRupees(safe.leftPaise), color = if (safe.leftPaise > 0) Pal.good else Pal.bad, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Per day, ${safe.daysLeft} ${if (safe.daysLeft == 1) "day" else "days"} to payday", color = Pal.muted, fontSize = 12.sp)
                        Text(short(safe.perDayPaise), color = if (safe.perDayPaise > 0) Pal.good else Pal.bad, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (safe.usingBudget) "Monthly budget" else if (state.incomeTyped) "Income (typed by you)" else "Income this pay cycle", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(short(safe.incomePaise), color = Pal.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("  Edit", color = Pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { editIncome = true })
                }
                Line("Repeats still to pay", "−" + short(safe.repeatsLeftPaise), Pal.bad)
                Line("Spent so far", "−" + short(safe.spentPaise), Pal.bad)
                if (safe.usingBudget) Text("No income known yet, so this starts from your budget. Tap Edit to type your income, or add a Salary repeat in Plans.", color = Pal.muted, fontSize = 11.sp)
            }
        }

        SectionTitle("Next 7 days")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.next7.isEmpty()) {
                    Text("No repeats are due this week. Add EMIs, rent and SIPs in Plans and they show up here.", color = Pal.muted, fontSize = 13.sp)
                } else {
                    state.next7.forEach { d -> DueRow(state, d, today) }
                    Text("You get a reminder at 9 am the day before and on the day.", color = Pal.muted, fontSize = 11.sp)
                }
            }
        }

        if (editIncome) IncomeSheet(state) { editIncome = false }

        SectionTitle("Latest")
        if (inRange.isEmpty()) {
            EmptyState("🫧", "Nothing in this period", "Spends and income show up here as bank messages arrive.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                inRange.take(6).forEach { TxnRow(state, it) { onEdit(it) } }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, tint: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Pal.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DueRow(state: AppState, d: RepeatDue, today: LocalDate) {
    val days = d.day.toEpochDay() - today.toEpochDay()
    val whenText = when {
        d.overdue -> "overdue since ${d.day.format(shortDay)}"
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        else -> "${d.day.format(shortDay)} · in $days days"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(repeatEmoji(d.repeat.rtype), fontSize = 22.sp)
        Column(Modifier.weight(1f)) {
            Text(d.repeat.title, color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(whenText, color = if (d.overdue) Pal.bad else if (days <= 1L) Pal.accent else Pal.muted, fontSize = 12.sp)
        }
        Text((if (d.repeat.type == TxnType.DEBIT) "−" else "+") + short(d.amountPaise), color = if (d.repeat.type == TxnType.DEBIT) Pal.fg else Pal.good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        SmallButton("Paid") { state.markPaid(d, true) }
    }
}
