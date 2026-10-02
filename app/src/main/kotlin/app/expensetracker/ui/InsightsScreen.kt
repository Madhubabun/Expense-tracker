package app.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.expensetracker.core.Insights
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import app.expensetracker.core.Tone
import java.time.LocalDate

/** Statistics: where the money goes, how this period compares with the last, and where you could save. */
@Composable
fun InsightsScreen(state: AppState, period: Period, anchor: LocalDate, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val all = state.reportTxns()
    val summary = Reports.summarize(all, period, anchor)
    val previous = Reports.summarize(all, period, Reports.shift(period, anchor, -1))
    val report = Insights.generate(all, period, anchor, state.budgetPaise)
    val word = when (period) { Period.DAY -> "day"; Period.WEEK -> "week"; Period.MONTH -> "month"; Period.YEAR -> "year" }
    val barsTitle = when (period) { Period.DAY -> "Last 7 days"; Period.WEEK -> "Day by day"; Period.MONTH -> "Week by week"; Period.YEAR -> "Month by month" }

    Column(
        Modifier.fillMaxSize().background(Pal.bg).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${summary.start} – ${summary.end}".uppercase(), color = Pal.muted, fontSize = 12.sp, letterSpacing = 1.sp)
                Text("Insights", color = Pal.fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            }
            Text("Close", color = Pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }

        if (summary.spentPaise == 0L) {
            EmptyState("📊", "Nothing to analyse yet", "Spend something this $word and your stats will show up here.")
            return@Column
        }

        HeroCard {
            Column {
                Text(if (report.potentialPaise > 0) "YOU COULD SAVE" else "TOTAL SPENT", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, letterSpacing = 1.sp)
                Text(
                    rupees(if (report.potentialPaise > 0) report.potentialPaise else summary.spentPaise).removeSuffix(".00"),
                    color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
                )
                Text(
                    if (report.potentialPaise > 0) "this $word, by trimming ${report.ideas.joinToString(" and ") { it.category }} a little" else "this $word. Nothing obvious to trim.",
                    color = Color.White.copy(alpha = .85f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SectionTitle("$barsTitle, against last $word")
        Card { SpendChart(summary.bars, previous.bars) }

        SectionTitle("Where it went")
        Card { CategoryBars(state, summary.byCategory, summary.spentPaise, limit = 8) }

        if (report.ideas.isNotEmpty()) {
            SectionTitle("Where you could save")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    report.ideas.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CategoryTile(state, i.category, size = 38.dp)
                            Column(Modifier.weight(1f)) {
                                Text("Trim ${i.category} by ${i.cutPercent}%", color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("You spent ${rupees(i.spentPaise).removeSuffix(".00")}", color = Pal.muted, fontSize = 12.sp)
                            }
                            Text("+" + rupees(i.savePaise).removeSuffix(".00"), color = Pal.good, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (report.insights.isNotEmpty()) {
            SectionTitle("What stands out")
            report.insights.forEach { tip ->
                val tint = when (tip.tone) { Tone.SAVE -> Pal.accent; Tone.WARN -> Pal.bad; Tone.GOOD -> Pal.good; Tone.INFO -> Pal.muted }
                Card {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(tint))
                        Text(tip.emoji, fontSize = 24.sp)
                        Column(Modifier.weight(1f)) {
                            Text(tip.title, color = Pal.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(tip.text, color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        Text("Tips come from your own numbers, on this phone. They are rules of thumb, not financial advice.", color = Pal.muted, fontSize = 11.sp)
    }
}
