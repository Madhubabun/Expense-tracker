package app.expensetracker.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Bar
import app.expensetracker.core.CategoryTotal
import app.expensetracker.core.Period
import app.expensetracker.core.Reports
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val rangeFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun ReportsScreen(state: AppState) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    var offset by rememberSaveable { mutableStateOf(0L) }

    val anchor = Reports.shift(period, LocalDate.now(), offset)
    val summary = remember(state.txns, period, offset) { Reports.summarize(state.reportTxns(), period, anchor) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p; offset = 0 },
                    label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { offset-- }) { Text("‹ Prev") }
            Text(
                "${summary.start.format(rangeFormat)} – ${summary.end.format(rangeFormat)}",
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("Next ›") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalCard("Spent", summary.spentPaise, DebitColor, Modifier.weight(1f))
            TotalCard("Received", summary.receivedPaise, CreditColor, Modifier.weight(1f))
        }
        Text(
            "Card bill payments and transfers are left out, so nothing is counted twice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            when (period) {
                Period.YEAR -> "Spending by month"
                else -> "Spending by day"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        BarChart(summary.bars)

        Text("Spending by category", style = MaterialTheme.typography.titleMedium)
        if (summary.byCategory.isEmpty()) {
            Text("No spending in this period.", style = MaterialTheme.typography.bodyMedium)
        } else {
            DonutChart(summary.byCategory, summary.spentPaise)
        }
    }
}

@Composable
private fun TotalCard(label: String, paise: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("₹" + Reports.formatRupees(paise), color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun BarChart(bars: List<Bar>) {
    val max = (bars.maxOfOrNull { it.spentPaise } ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.7f
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - barWidth) / 2
                drawRect(trackColor, Offset(x, size.height - 2f), Size(barWidth, 2f))
                val h = size.height * (b.spentPaise.toFloat() / max)
                if (h > 0f) drawRect(barColor, Offset(x, size.height - h), Size(barWidth, h))
            }
        }
        if (bars.size <= 12) {
            Row(Modifier.fillMaxWidth()) {
                bars.forEach { Text(it.label, Modifier.weight(1f), fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bars.first().label, fontSize = 10.sp)
                Text(bars[bars.size / 2].label, fontSize = 10.sp)
                Text(bars.last().label, fontSize = 10.sp)
            }
        }
        val top = bars.maxByOrNull { it.spentPaise }
        if (top != null && top.spentPaise > 0) {
            Text(
                "Highest: ₹${Reports.formatRupees(top.spentPaise)} on ${top.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DonutChart(items: List<CategoryTotal>, total: Long) {
    // Show the 7 biggest categories; fold the rest into "Other".
    val top = items.take(7)
    val restTotal = items.drop(7).sumOf { it.spentPaise }
    val slices = if (restTotal > 0) top + CategoryTotal("Other", restTotal) else top

    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 36f
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * (s.spentPaise.toFloat() / total)
                drawArc(
                    color = ChartColors[i % ChartColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Text("₹" + Reports.formatRupees(total), fontWeight = FontWeight.Bold)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(ChartColors[i % ChartColors.size], CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(s.category, Modifier.weight(1f))
                val pct = (100f * s.spentPaise / total).toInt()
                Text("₹${Reports.formatRupees(s.spentPaise)}  ($pct%)")
            }
        }
    }
}
