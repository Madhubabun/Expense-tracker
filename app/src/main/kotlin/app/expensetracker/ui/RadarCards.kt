package app.expensetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Categories
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.core.WhatIf
import app.expensetracker.data.AccountKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

private fun short(paise: Long) = rupees(paise).removeSuffix(".00")

/** Subscriptions, SIPs and EMIs spotted in your history, with what they cost a year and any price rise. */
@Composable
fun SubscriptionRadarCard(state: AppState) {
    val today = LocalDate.now().toEpochDay()
    SectionTitle("Subscription radar")
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.subscriptions.isEmpty()) {
                Text("Nothing yet. A payment shows up here once it has repeated about a month (or a year) apart.", color = Pal.muted, fontSize = 13.sp)
            } else {
                val yearly = state.subscriptions.sumOf { it.yearlyPaise }
                Text("${short(yearly)} a year across ${state.subscriptions.size} regular payments", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                state.subscriptions.forEach { s ->
                    val days = s.nextDay - today
                    val whenText = when {
                        days < 0L -> "was due ${LocalDate.ofEpochDay(s.nextDay).format(dayFormat)}"
                        days == 0L -> "due today"
                        days == 1L -> "due tomorrow"
                        else -> "next ${LocalDate.ofEpochDay(s.nextDay).format(dayFormat)}"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.label, color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${if (s.everyDays > 300) "Yearly" else "Monthly"} · $whenText", color = Pal.muted, fontSize = 12.sp)
                            if (s.priceUp) Text("Price went up from ${short(s.oldAmountPaise)}", color = Pal.bad, fontSize = 12.sp)
                        }
                        Text(short(s.amountPaise), color = Pal.fg, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Repeats due in the next 30 days, and whether what is left this month covers them. */
@Composable
fun MoneyCalendarCard(state: AppState) {
    val today = LocalDate.now()
    val due = state.upcoming(30)
    val out = due.filter { it.repeat.type == TxnType.DEBIT }.sumOf { it.amountPaise }
    SectionTitle("Next 30 days")
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (due.isEmpty()) {
                Text("No repeats are due. Add EMIs, rent and SIPs in Plans to see them here.", color = Pal.muted, fontSize = 13.sp)
            } else {
                Text("${short(out)} going out across ${due.size} ${if (due.size == 1) "repeat" else "repeats"}", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val left = state.safe.leftPaise
                Text(
                    if (left >= 0) "You have ${short(left)} left to spend this month after the repeats still to pay."
                    else "You are ${short(-left)} short this month once the repeats still to pay are counted.",
                    color = if (left >= 0) Pal.muted else Pal.bad, fontSize = 12.sp,
                )
                due.take(10).forEach { d ->
                    val days = d.day.toEpochDay() - today.toEpochDay()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${repeatEmoji(d.repeat.rtype)}  ${d.repeat.title}", color = Pal.fg, fontSize = 14.sp, maxLines = 1)
                            Text(if (d.overdue) "overdue since ${d.day.format(dayFormat)}" else d.day.format(dayFormat) + if (days > 1) " · in $days days" else "", color = if (d.overdue) Pal.bad else Pal.muted, fontSize = 12.sp)
                        }
                        Text((if (d.repeat.type == TxnType.DEBIT) "−" else "+") + short(d.amountPaise), color = if (d.repeat.type == TxnType.DEBIT) Pal.fg else Pal.good, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** Slide how much you would cut a category by and see the yearly saving and when your goal arrives. */
@Composable
fun WhatIfCard(state: AppState) {
    val today = LocalDate.now().toEpochDay()
    val perMonth = state.txns.filter {
        it.type == TxnType.DEBIT && it.kind == TxnKind.NORMAL && it.category.isNotEmpty() && it.category !in Categories.excludedFromTotals && it.epochDay > today - 90
    }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountPaise } / 3 }.entries.sortedByDescending { it.value }.take(5)
    if (perMonth.isEmpty()) return
    val cuts = remember { mutableStateMapOf<String, Float>() }
    val saving = perMonth.sumOf { WhatIf.cut(it.value, (cuts[it.key] ?: 0f).toInt()) }
    val goal = state.goals.firstOrNull { it.savings && it.savedPaise < it.targetPaise }

    SectionTitle("What if you cut back")
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            perMonth.forEach { (cat, monthly) ->
                val pct = (cuts[cat] ?: 0f).toInt()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$cat · about ${short(monthly)} a month", color = Pal.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("−$pct%", color = Pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(value = cuts[cat] ?: 0f, onValueChange = { cuts[cat] = (it / 5).toInt() * 5f }, valueRange = 0f..100f)
            }
            Text("You would keep ${short(saving)} a month, ${short(saving * 12)} a year.", color = Pal.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (goal != null) {
                val left = goal.targetPaise - goal.savedPaise
                val months = WhatIf.months(left, saving)
                Text(
                    if (months == null) "Move a slider to see when you would reach ${goal.name}."
                    else "That would reach ${goal.emoji} ${goal.name} in about $months ${if (months == 1) "month" else "months"}.",
                    color = Pal.muted, fontSize = 13.sp,
                )
            }
        }
    }
}

/** A short, shareable read on how you spend: weekend, late night, small sips or one big category. */
@Composable
fun PersonaCard(state: AppState) {
    val context = LocalContext.current
    val persona = state.persona() ?: return
    SectionTitle("Your spending personality")
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${persona.emoji}  ${persona.title}", color = Pal.fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            persona.facts.forEach { Text("• $it", color = Pal.muted, fontSize = 13.sp) }
            SmallButton("Share") {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "${persona.emoji} I'm a ${persona.title}\n" + persona.facts.joinToString("\n") { "• $it" } + "\n— from Spendr")
                }
                context.startActivity(android.content.Intent.createChooser(send, "Share"))
            }
        }
    }
}
