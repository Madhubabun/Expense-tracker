package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.CategoryTotal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val billDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Category budget progress and upcoming monthly bills, shown on the current month only. */
@Composable
fun MonthExtras(state: AppState, byCategory: List<CategoryTotal>, today: LocalDate) {
    val stats = state.todayStats()
    val o = stats.outlook
    if (state.budgetPaise > 0) {
        SectionTitle("Month-end forecast")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "You're heading for " + rupees(o.projectedPaise).removeSuffix(".00"),
                    color = if (o.overBudget) Pal.bad else Pal.good, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (o.overBudget) "That is ${rupees(o.projectedPaise - state.budgetPaise).removeSuffix(".00")} over your ${rupees(state.budgetPaise).removeSuffix(".00")} budget. Spending about ${rupees(o.perDayPaise.coerceAtLeast(0)).removeSuffix(".00")} a day from here keeps you inside it."
                    else "Inside your ${rupees(state.budgetPaise).removeSuffix(".00")} budget, with ${rupees((state.budgetPaise - o.projectedPaise).coerceAtLeast(0)).removeSuffix(".00")} to spare.",
                    color = Pal.muted, fontSize = 13.sp,
                )
                Text("Based on your daily habit so far plus bills and repeats still to come.", color = Pal.muted, fontSize = 11.sp)
            }
        }
    }

    val budgets = state.categoryBudgets
    if (budgets.isNotEmpty()) {
        SectionTitle("Category budgets")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                budgets.entries.sortedBy { it.key }.forEach { (name, limit) ->
                    val spent = byCategory.firstOrNull { it.category == name }?.spentPaise ?: 0L
                    val frac = spent.toFloat() / limit.coerceAtLeast(1)
                    val tint = when { frac >= 1f -> Pal.bad; frac >= .8f -> Color(0xFFFFB020); else -> state.colorOf(name) }
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CategoryTile(state, name, size = 28.dp)
                            Text(name, color = Pal.fg, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            Text("${rupees(spent).removeSuffix(".00")} / ${rupees(limit).removeSuffix(".00")}", color = Pal.muted, fontSize = 12.sp)
                        }
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.surface2)) {
                            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(tint))
                        }
                    }
                }
            }
        }
    }

    PersonaCard(state)
    WhatIfCard(state)
    val bills = state.bills()
    if (bills.isNotEmpty()) {
        SectionTitle("Upcoming bills")
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                bills.forEach { b ->
                    val days = b.nextDay - today.toEpochDay()
                    val whenText = when {
                        days < 0L -> "was due ${LocalDate.ofEpochDay(b.nextDay).format(billDate)}"
                        days == 0L -> "due today"
                        days == 1L -> "due tomorrow"
                        else -> "due ${LocalDate.ofEpochDay(b.nextDay).format(billDate)}"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.label, color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(whenText, color = if (days <= 1) Pal.accent else Pal.muted, fontSize = 12.sp)
                        }
                        Text("~" + rupees(b.amountPaise).removeSuffix(".00"), color = Pal.fg, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
