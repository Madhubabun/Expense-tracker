package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

/** Money that came in: salary, refunds and anything received, grouped by who it came from. */
@Composable
fun IncomeScreen(state: AppState, period: Period, offset: Int, onPeriod: (Period) -> Unit, onOffset: (Int) -> Unit, onAdd: () -> Unit, onEdit: (Txn) -> Unit) {
    val anchor = Reports.shift(period, LocalDate.now(), offset.toLong())
    val (start, end) = Reports.range(period, anchor)
    val credits = state.txns.filter {
        val d = LocalDate.ofEpochDay(it.epochDay)
        it.type == TxnType.CREDIT && it.kind == TxnKind.NORMAL && it.category !in Categories.excludedFromTotals && !d.isBefore(start) && !d.isAfter(end)
    }
    val total = credits.sumOf { it.amountPaise }
    val bySource = credits.groupBy { (it.merchant ?: it.category).ifBlank { "Other" } }
        .map { (k, v) -> k to v.sumOf { it.amountPaise } }.sortedByDescending { it.second }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Money in", "Income")
        PeriodBar(period, offset, onPeriod, onOffset)
        HeroCard {
            Column {
                Text("TOTAL INCOME", color = Color.White.copy(alpha = .8f), fontSize = 11.sp, letterSpacing = 1.sp)
                Text(rupees(total).removeSuffix(".00"), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("${credits.size} ${if (credits.size == 1) "payment" else "payments"} received", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
        }
        SmallButton("＋ Add income", primary = true) { onAdd() }
        if (credits.isEmpty()) {
            EmptyState("💸", "No income in this period", "Salary, refunds and money received show up here from your bank messages.")
        } else {
            SectionTitle("By source")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bySource.take(8).forEach { (name, paise) ->
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                Text(name, color = Pal.fg, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(rupees(paise).removeSuffix(".00"), color = Pal.good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(7.dp).clip(CircleShape).background(Pal.surface2)) {
                                Box(Modifier.fillMaxWidth((paise.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)).height(7.dp).clip(CircleShape).background(Pal.good))
                            }
                        }
                    }
                }
            }
            SectionTitle("Received")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                credits.take(50).forEach { TxnRow(state, it) { onEdit(it) } }
            }
        }
    }
}
