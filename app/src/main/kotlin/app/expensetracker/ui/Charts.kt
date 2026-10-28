package app.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Bar
import app.expensetracker.core.CategoryTotal
import java.util.Locale

/** 6,350 stays as is; 12,300 becomes 12k; 1,23,000 becomes 1.2L. Short enough to sit on top of a bar. */
fun shortRupees(paise: Long): String {
    val r = paise / 100
    return when {
        r <= 0 -> ""
        r < 10_000 -> "%,d".format(Locale.ENGLISH, r)
        r < 100_000 -> "%.0fk".format(Locale.ENGLISH, r / 1000.0)
        else -> "%.1fL".format(Locale.ENGLISH, r / 100_000.0).replace(".0L", "L")
    }
}

/**
 * Bar chart you can read at a glance: amount above every bar, label below, the biggest bar lit up and a dotted
 * line for the average. Pass [previous] to show last period's bars in grey beside this period's.
 */
@Composable
fun SpendChart(bars: List<Bar>, previous: List<Bar>? = null) {
    val top = maxOf(bars.maxOfOrNull { it.spentPaise } ?: 0L, previous?.maxOfOrNull { it.spentPaise } ?: 0L).coerceAtLeast(1L)
    val nonZero = bars.filter { it.spentPaise > 0 }
    val avg = if (nonZero.isEmpty()) 0L else bars.sumOf { it.spentPaise } / bars.size
    val peak = bars.indexOfFirst { it.spentPaise == (bars.maxOfOrNull { b -> b.spentPaise } ?: 0L) && it.spentPaise > 0 }
    val chartHeight = 150.dp
    val barZone = 112.dp
    val line = Pal.muted
    val many = bars.size > 8

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                if (avg > 0) {
                    val y = size.height - barZone.toPx() * (avg.toFloat() / top)
                    drawLine(line.copy(alpha = .7f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                }
            }
            Row(Modifier.fillMaxWidth().height(chartHeight), verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { i, b ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(shortRupees(b.spentPaise), color = if (i == peak) Pal.fg else Pal.muted, fontSize = if (many) 8.sp else 10.sp, fontWeight = if (i == peak) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            previous?.getOrNull(i)?.let { p ->
                                Box(Modifier.width(if (many) 6.dp else 11.dp).height(maxOf(3.dp, barZone * (p.spentPaise.toFloat() / top))).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Pal.muted.copy(alpha = .35f)))
                            }
                            val h = maxOf(3.dp, barZone * (b.spentPaise.toFloat() / top))
                            val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            Box(
                                Modifier.width(if (previous != null) (if (many) 8.dp else 16.dp) else if (many) 14.dp else 26.dp).height(h).clip(shape)
                                    .background(if (i == peak) Brush.verticalGradient(listOf(Pal.pink, Pal.accent)) else Brush.verticalGradient(listOf(Pal.accent.copy(alpha = .75f), Pal.accent.copy(alpha = .45f)))),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEachIndexed { i, b ->
                Text(
                    if (many) b.label.take(1) else b.label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                    color = if (i == peak) Pal.fg else Pal.muted, fontWeight = if (i == peak) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (avg > 0) Text("- - -  average ₹${shortRupees(avg)}", color = Pal.muted, fontSize = 11.sp)
            if (peak >= 0) Text("● highest ${bars[peak].label} ₹${shortRupees(bars[peak].spentPaise)}", color = Pal.pink, fontSize = 11.sp)
            if (previous != null) Text("▮ last time", color = Pal.muted, fontSize = 11.sp)
        }
    }
}

/** Ranked bars with the share each category takes. Shows the top few; tap to see the rest. */
@Composable
fun CategoryBars(state: AppState, items: List<CategoryTotal>, total: Long, limit: Int = 6) {
    if (items.isEmpty() || total <= 0) return
    var all by remember { mutableStateOf(false) }
    val shown = if (all) items else items.take(limit)
    val top = items.first().spentPaise.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.forEach { c ->
            val share = Math.round(c.spentPaise * 100f / total)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTile(state, c.category, size = 34.dp)
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(c.category, color = Pal.fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(rupees(c.spentPaise).removeSuffix(".00") + "  ·  $share%", color = Pal.muted, fontSize = 12.sp)
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(7.dp).clip(CircleShape).background(Pal.surface2)) {
                        Box(Modifier.fillMaxWidth(c.spentPaise.toFloat() / top).height(7.dp).clip(CircleShape).background(state.colorOf(c.category)))
                    }
                }
            }
        }
        if (items.size > limit) {
            Text(
                if (all) "Show fewer" else "Show all ${items.size}", color = Pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { all = !all }.padding(vertical = 4.dp),
            )
        }
    }
}
