package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun Period.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** The date range a period and offset point at, in words: "Today · 2 Oct", "26 Sep – 2 Oct", "October 2026", "2026". */
fun periodLabel(period: Period, offset: Int): String {
    val anchor = Reports.shift(period, LocalDate.now(), offset.toLong())
    val (start, end) = Reports.range(period, anchor)
    return when (period) {
        Period.DAY -> if (offset == 0) "Today · ${anchor.format(dayFmt)}" else anchor.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
        Period.WEEK -> "${start.format(dayFmt)} – ${end.format(dayFmt)}"
        Period.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        Period.YEAR -> anchor.year.toString()
    }
}

/** Day, Week, Month and Year in a dropdown, with arrows to move back and forward. Used on every tab but Plans. */
@Composable
fun PeriodBar(period: Period, offset: Int, onPeriod: (Period) -> Unit, onOffset: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Text(
                period.label() + " ▾", color = Pal.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Pal.surface2).border(1.dp, Pal.line, CircleShape)
                    .clickable { open = true }.padding(horizontal = 16.dp, vertical = 9.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Period.entries.forEach { p ->
                    DropdownMenuItem(text = { Text(p.label()) }, onClick = { open = false; onPeriod(p) })
                }
            }
        }
        StepArrow("‹") { onOffset(offset - 1) }
        Text(periodLabel(period, offset), color = Pal.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        StepArrow("›", enabled = offset < 0) { onOffset(offset + 1) }
    }
}

@Composable
private fun StepArrow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(Pal.surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) Pal.fg else Pal.muted.copy(alpha = .4f), fontSize = 20.sp) }
}
