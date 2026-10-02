package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Investment
import app.expensetracker.data.RepeatDue
import app.expensetracker.data.Goal
import app.expensetracker.data.Loan
import app.expensetracker.data.Repeat
import java.time.LocalDate

/** Plans: repeats (EMIs, rent, SIPs, tithe), investments, goals and loans. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlansScreen(state: AppState) {
    var goalNew by remember { mutableStateOf(false) }
    var goalEditing by remember { mutableStateOf<Goal?>(null) }
    var goalAdding by remember { mutableStateOf<Goal?>(null) }
    var loanNew by remember { mutableStateOf(false) }
    var loanEditing by remember { mutableStateOf<Loan?>(null) }
    var loanPaying by remember { mutableStateOf<Loan?>(null) }
    var repeatNew by remember { mutableStateOf(false) }
    var repeatEditing by remember { mutableStateOf<Repeat?>(null) }
    var investNew by remember { mutableStateOf(false) }
    var investEditing by remember { mutableStateOf<Investment?>(null) }
    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1).toEpochDay()
    val dues = state.dues()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Everything that repeats", "Plans")

        // Repeats
        SectionTitle("Repeats every month")
        if (state.repeats.isEmpty()) Text("EMIs, loans, rent, SIPs, insurance, tithe or salary: add each once and you get reminders and a truer safe-to-spend.", color = Pal.muted, fontSize = 13.sp)
        dues.forEach { d ->
            val r = d.repeat
            Card(Modifier.clickable { repeatEditing = r }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(repeatEmoji(r.rtype), fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text(r.title, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${r.rtype} · day ${r.dayOfMonth} of every month" + (if (r.pct > 0) " · ${r.pct}% of income" else "") +
                                if (d.paid) " · paid this month ✓" else "",
                            color = if (d.paid) Pal.good else Pal.muted, fontSize = 12.sp,
                        )
                    }
                    Text((if (r.type == TxnType.DEBIT) "−" else "+") + rupees(d.amountPaise).removeSuffix(".00"), color = if (r.type == TxnType.DEBIT) Pal.fg else Pal.good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        AddButton("＋ New repeat") { repeatNew = true }

        // Investments
        SectionTitle("Investments")
        val invested = state.investments.sumOf { it.investedPaise }
        val worth = state.investments.sumOf { if (it.valuePaise > 0) it.valuePaise else it.investedPaise }
        if (state.investments.isEmpty()) Text("Note down your SIPs, stocks, gold or FDs to see what you put in against what it is worth.", color = Pal.muted, fontSize = 13.sp)
        else Card {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text("Invested", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(rupees(invested).removeSuffix(".00"), color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Row {
                    Text("Worth today", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    val gain = if (invested > 0) Math.round((worth - invested) * 100.0 / invested) else 0L
                    Text(rupees(worth).removeSuffix(".00") + (if (invested > 0) "  (" + (if (gain >= 0) "+" else "") + gain + "%)" else ""), color = if (worth >= invested) Pal.good else Pal.bad, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        state.investments.forEach { i ->
            Card(Modifier.clickable { investEditing = i }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.name, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Invested ${rupees(i.investedPaise).removeSuffix(".00")}", color = Pal.muted, fontSize = 12.sp)
                    }
                    Text(if (i.valuePaise > 0) rupees(i.valuePaise).removeSuffix(".00") else "—", color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        AddButton("＋ New investment") { investNew = true }

        // Goals
        SectionTitle("Goals")
        if (state.goals.isEmpty()) Text("Save for a trip or cap what you spend on something. Add your first goal.", color = Pal.muted, fontSize = 13.sp)
        state.goals.forEach { g ->
            val frac = (g.savedPaise.toFloat() / g.targetPaise.coerceAtLeast(1)).coerceIn(0f, 1f)
            val left = (g.targetPaise - g.savedPaise).coerceAtLeast(0)
            val daysLeft = if (g.endDay > 0) g.endDay - today.toEpochDay() else 0
            Card(Modifier.clickable { goalEditing = g }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(g.emoji, fontSize = 26.sp)
                        Column(Modifier.weight(1f)) {
                            Text(g.name, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (g.savings) "Savings goal" else "Spending limit", color = Pal.muted, fontSize = 11.sp)
                        }
                        Text("${rupees(g.savedPaise).removeSuffix(".00")} / ${rupees(g.targetPaise).removeSuffix(".00")}", color = Pal.fg, fontSize = 13.sp)
                    }
                    ProgressBar(frac, if (!g.savings && frac >= 1f) Pal.bad else Pal.accent)
                    if (left > 0 && daysLeft > 0) {
                        Text(
                            if (g.savings) "Save ${rupees(left / daysLeft).removeSuffix(".00")} a day for the next $daysLeft days" else "${rupees(left).removeSuffix(".00")} left, about ${rupees(left / daysLeft).removeSuffix(".00")} a day",
                            color = Pal.muted, fontSize = 12.sp,
                        )
                    } else if (left == 0L) {
                        Text(if (g.savings) "Goal reached 🎉" else "Limit reached", color = Pal.accent, fontSize = 12.sp)
                    }
                    SmallButton(if (g.savings) "＋ Add money" else "＋ Add spend") { goalAdding = g }
                }
            }
        }
        AddButton("＋ New goal") { goalNew = true }

        // Loans
        SectionTitle("Loans")
        val owe = state.loans.filter { it.borrowed }.sumOf { it.leftPaise }
        val lent = state.loans.filter { !it.borrowed }.sumOf { it.leftPaise }
        if (state.loans.isEmpty()) Text("Money you owe or lent, with reminders when it is due.", color = Pal.muted, fontSize = 13.sp)
        else Text("You owe ${rupees(owe).removeSuffix(".00")}  ·  Owed to you ${rupees(lent).removeSuffix(".00")}", color = Pal.muted, fontSize = 13.sp)
        state.loans.forEach { l ->
            val frac = (l.paidPaise.toFloat() / l.totalPaise.coerceAtLeast(1)).coerceIn(0f, 1f)
            Card(Modifier.clickable { loanEditing = l }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (l.borrowed) "You owe" else "You lent") + if (l.dueDay > 0) " · due ${LocalDate.ofEpochDay(l.dueDay)}" else "",
                                color = Pal.muted, fontSize = 11.sp,
                            )
                        }
                        Text(rupees(l.leftPaise).removeSuffix(".00") + " left", color = if (l.borrowed) Pal.bad else Pal.good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ProgressBar(frac, if (l.borrowed) Pal.accent else Pal.good)
                    SmallButton(if (l.borrowed) "＋ Record payment" else "＋ Record repayment") { loanPaying = l }
                }
            }
        }
        AddButton("＋ New loan") { loanNew = true }

        // Tags
        val monthSpends = state.txns.filter { it.type == TxnType.DEBIT && it.epochDay >= monthStart && it.kind == app.expensetracker.core.TxnKind.NORMAL }
        val byTag = monthSpends.flatMap { t -> state.tagsOf(t).map { it to t.amountPaise } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
        SectionTitle("Tags this month")
        if (byTag.isEmpty()) Text("Add tags like #trip or #work when you edit a spend, and totals show up here.", color = Pal.muted, fontSize = 13.sp)
        else androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            byTag.forEach { (tag, sum) -> Pill("#$tag  ${rupees(sum).removeSuffix(".00")}") }
        }
    }

    if (goalNew) GoalSheet(state, null) { goalNew = false }
    goalEditing?.let { GoalSheet(state, it) { goalEditing = null } }
    goalAdding?.let { g -> AmountSheet(if (g.savings) "Add to ${g.name}" else "Spent on ${g.name}", "Add", { state.addToGoal(g, it) }) { goalAdding = null } }
    if (loanNew) LoanSheet(state, null) { loanNew = false }
    loanEditing?.let { LoanSheet(state, it) { loanEditing = null } }
    loanPaying?.let { l -> AmountSheet(if (l.borrowed) "Payment on ${l.name}" else "Repayment for ${l.name}", "Record", { state.addToLoan(l, it) }) { loanPaying = null } }
    if (repeatNew) RepeatSheet(state, null) { repeatNew = false }
    repeatEditing?.let { RepeatSheet(state, it) { repeatEditing = null } }
    if (investNew) InvestSheet(state, null) { investNew = false }
    investEditing?.let { InvestSheet(state, it) { investEditing = null } }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Pal.accent.copy(alpha = .9f)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
private fun ProgressBar(frac: Float, tint: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Pal.surface2)) {
        Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50)).background(tint))
    }
}

fun signedRupees(paise: Long): String = (if (paise < 0) "−" else "") + "₹" + Reports.formatRupees(Math.abs(paise)).removeSuffix(".00")
