package app.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only today. No weekly, monthly or yearly numbers live on this screen. */
@Composable
fun TodayScreen(state: AppState, onEdit: (Txn) -> Unit) {
    val today = LocalDate.now()
    val todays = state.txns.filter { it.epochDay == today.toEpochDay() }
    val countable = state.reportTxns().filter { it.date == today && it.countable && it.type == TxnType.DEBIT }
    val spentToday = countable.sumOf { it.amountPaise }
    val monthSpent = Reports.summarize(state.reportTxns(), Period.MONTH, today).spentPaise
    val budget = state.budgetPaise.coerceAtLeast(1)
    val used = (monthSpent.toFloat() / budget).coerceIn(0f, 1f)
    val left = (budget - monthSpent).coerceAtLeast(0)
    val need = state.needsCategory

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(today.format(DateTimeFormatter.ofPattern("EEEE · d MMM yyyy", Locale.ENGLISH)), "Today") {
            if (need.isNotEmpty()) {
                Pill("${need.size} need a category", onClick = { onEdit(need.first()) })
            }
        }

        HeroCard {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Spent today", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("₹", color = Color.White.copy(alpha = .75f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp, end = 2.dp))
                            Text(Reports.formatRupees(spentToday).removeSuffix(".00"), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        }
                    }
                    Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 10.dp.toPx()
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            val topLeft = Offset(stroke / 2, stroke / 2)
                            drawArc(Color.White.copy(alpha = .25f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                            drawArc(Color.White, -90f, 360f * used, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${Math.round(monthSpent * 100f / budget)}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("OF BUDGET", color = Color.White.copy(alpha = .78f), fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${rupees(left)} left of your ${rupees(state.budgetPaise)} ${today.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))} budget.",
                    color = Color.White.copy(alpha = .85f), fontSize = 13.sp,
                )
            }
        }

        val stats = state.todayStats()
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val safe = stats.outlook.safeTodayPaise
                Text("SAFE TO SPEND TODAY", color = Pal.muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(
                    if (safe > 0) rupees(safe).removeSuffix(".00") else "Budget's used up",
                    color = if (safe > 0) Pal.good else Pal.bad, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                )
                Text(
                    if (stats.outlook.availablePaise > 0)
                        "About ${rupees(stats.outlook.perDayPaise).removeSuffix(".00")} a day for the next ${stats.outlook.daysLeft} days" +
                            if (stats.committedPaise > 0) ", with ${rupees(stats.committedPaise).removeSuffix(".00")} set aside for bills" else ""
                    else "You're past this month's budget once upcoming bills are counted.",
                    color = Pal.muted, fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (stats.streak >= 1) Pill("🔥 ${stats.streak} no-spend ${if (stats.streak == 1) "day" else "days"}", tint = Pal.pink)
                    Pill("Week score ${stats.score} · " + (if (stats.score >= 80) "Great" else if (stats.score >= 55) "Okay" else "Needs care"), tint = Pal.accent)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Today’s spends", Modifier.weight(1f))
            Text("Tap to tag · swipe ← to edit", color = Pal.muted, fontSize = 11.sp, letterSpacing = .5.sp)
        }

        if (todays.isEmpty()) {
            EmptyState("🫧", "Zero spends today", "Your wallet is chilling. Enjoy the quiet, we’ll be here when it gets loud.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                todays.take(5).forEach { TxnRow(state, it) { onEdit(it) } }
            }
            if (todays.size > 5) {
                Text("+${todays.size - 5} more today", color = Pal.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
    }
}
