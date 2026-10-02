package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Categories
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** 6,350 stays as is; 12,300 becomes 12.3k; 1,23,000 becomes 1.2L. Keeps the day cells tidy. */
private fun short(paise: Long): String {
    val r = paise / 100
    return when {
        r < 10_000 -> "%,d".format(Locale.ENGLISH, r)
        r < 100_000 -> "%.1fk".format(Locale.ENGLISH, r / 1000.0).replace(".0k", "k")
        else -> "%.1fL".format(Locale.ENGLISH, r / 100_000.0).replace(".0L", "L")
    }
}

/** Month grid with what you spent (red) and received (green) each day. Tap a day to see its spends. */
@Composable
fun MonthCalendar(state: AppState, month: YearMonth, today: LocalDate, onEdit: (Txn) -> Unit) {
    val inMonth = state.txns.filter { val d = LocalDate.ofEpochDay(it.epochDay); YearMonth.from(d) == month }
    val counted = inMonth.filter { it.kind == TxnKind.NORMAL && it.category !in Categories.excludedFromTotals }
    val spent = counted.filter { it.type == TxnType.DEBIT }.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.amountPaise } }
    val got = counted.filter { it.type == TxnType.CREDIT }.groupBy { it.epochDay }.mapValues { e -> e.value.sumOf { it.amountPaise } }
    var selected by rememberSaveable(month.toString()) { mutableStateOf(if (YearMonth.from(today) == month) today.toEpochDay() else -1L) }

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach {
                    Text(
                        it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), color = Pal.muted, fontSize = 11.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value % 7 // Sunday = 0
            val cells = lead + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - lead + 1
                        if (dayNum < 1 || dayNum > month.lengthOfMonth()) {
                            Box(Modifier.weight(1f).height(54.dp))
                        } else {
                            val date = month.atDay(dayNum)
                            val epoch = date.toEpochDay()
                            val isSel = selected == epoch
                            Column(
                                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Pal.accent.copy(alpha = .28f) else Pal.surface2.copy(alpha = .5f))
                                    .border(1.dp, if (date == today) Pal.accent else Pal.line.copy(alpha = .4f), RoundedCornerShape(10.dp))
                                    .clickable { selected = if (isSel) -1L else epoch }.padding(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$dayNum", color = if (date == today) Pal.accent else Pal.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                spent[epoch]?.let { Text(short(it), color = Pal.bad, fontSize = 9.5.sp, maxLines = 1) }
                                got[epoch]?.let { Text(short(it), color = Pal.good, fontSize = 9.5.sp, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selected >= 0) {
        val list = inMonth.filter { it.epochDay == selected }
        val d = LocalDate.ofEpochDay(selected)
        SectionTitle(dayHeading(d, today))
        if (list.isEmpty()) Text("Nothing on this day.", color = Pal.muted, fontSize = 13.sp)
        else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { list.forEach { TxnRow(state, it) { onEdit(it) } } }
    }
}

/** List or calendar switch for the Month screen. */
@Composable
fun ViewToggle(calendar: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Pal.surface2).padding(3.dp)) {
        listOf(false to "List", true to "Calendar").forEach { (cal, label) ->
            val on = calendar == cal
            Text(
                label, color = if (on) androidx.compose.ui.graphics.Color.White else Pal.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape)
                    .background(if (on) Pal.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(cal) }.padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}
