package app.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import java.time.LocalDate

/** Charts and the clever bits: where the money went, savings tips, subscriptions, what is due, what-ifs. */
@Composable
fun StatsScreen(state: AppState, period: Period, offset: Int, onPeriod: (Period) -> Unit, onOffset: (Int) -> Unit, onInsights: (Period, LocalDate) -> Unit) {
    val today = LocalDate.now()
    val anchor = Reports.shift(period, today, offset.toLong())
    val summary = Reports.summarize(state.reportTxns(), period, anchor)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Your money in numbers", "Statistics")
        PeriodBar(period, offset, onPeriod, onOffset)

        if (summary.txnCount == 0) {
            EmptyState("📊", "Nothing to chart yet", "Spend or receive something in this period and the charts appear here.")
        } else {
            IncomeVsSpending(summary.spentPaise, summary.receivedPaise)
            if (summary.investedPaise > 0) Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📈  Invested", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(rupees(summary.investedPaise).removeSuffix(".00"), color = Pal.good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            SectionTitle(when (period) { Period.DAY -> "Last 7 days"; Period.WEEK -> "Spending by day"; Period.MONTH -> "Spending by week"; Period.YEAR -> "Spending by month" })
            Card { SpendChart(summary.bars) }
            if (summary.byCategory.isNotEmpty()) {
                SectionTitle("Where it went")
                Card { Donut(state, summary.byCategory, summary.spentPaise) }
                Card { CategoryBars(state, summary.byCategory, summary.spentPaise) }
            }
            Card(Modifier.clickable { onInsights(period, anchor) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✨", fontSize = 26.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Insights and savings tips", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("See what stands out and where you could save", color = Pal.muted, fontSize = 12.sp)
                    }
                    Text("›", color = Pal.muted, fontSize = 22.sp)
                }
            }
        }

        MoneyCalendarCard(state)
        SubscriptionRadarCard(state)
        WhatIfCard(state)
        PersonaCard(state)
    }
}
