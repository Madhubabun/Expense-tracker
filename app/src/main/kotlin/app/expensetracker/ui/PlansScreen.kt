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

/** Plans: repeats (EMIs, rent, SIPs, tithe) and investments. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlansScreen(state: AppState) {
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
        val (paidDues, openDues) = dues.partition { it.paid }
        openDues.forEach { d -> RepeatCard(state, d) { repeatEditing = d.repeat } }
        if (paidDues.isNotEmpty()) {
            SectionTitle("Paid this cycle")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paidDues.forEach { d ->
                        Row(Modifier.fillMaxWidth().clickable { repeatEditing = d.repeat }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(repeatEmoji(d.repeat.rtype), fontSize = 20.sp)
                            Text(d.repeat.title, color = Pal.fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text((if (d.repeat.type == TxnType.DEBIT) "−" else "+") + rupees(d.amountPaise).removeSuffix(".00"), color = Pal.muted, fontSize = 14.sp)
                            Text("✓", color = Pal.good, fontSize = 15.sp)
                        }
                    }
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
    }

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

private fun dayWords(r: Repeat) = if (r.dayOfMonth >= app.expensetracker.core.RepeatPlan.LAST_WORKING_DAY) "last working day" else "day ${r.dayOfMonth}"

@Composable
private fun RepeatCard(state: AppState, d: RepeatDue, onClick: () -> Unit) {
    val r = d.repeat
    Card(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(repeatEmoji(r.rtype), fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Text(r.title, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val kind = if (r.title.contains(r.rtype, ignoreCase = true)) "" else "${r.rtype} · "
                Text(
                    kind + dayWords(r) + " of every month" + if (r.pct > 0) " · ${r.pct}% of income" else "",
                    color = if (d.overdue) Pal.bad else Pal.muted, fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (r.type == TxnType.DEBIT) "−" else "+") + rupees(d.amountPaise).removeSuffix(".00"), color = if (r.type == TxnType.DEBIT) Pal.fg else Pal.good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                SmallButton("Paid") { state.markPaid(d, true) }
            }
        }
    }
}
