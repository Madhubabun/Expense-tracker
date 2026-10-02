package app.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Bar
import app.expensetracker.core.CategoryTotal
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Category
import app.expensetracker.data.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun timeText(millis: Long): String =
    if (millis <= 0) "" else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat).lowercase()

fun rupees(paise: Long): String = "₹" + Reports.formatRupees(paise)

fun dayHeading(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
}

/** Square icon for a category: the picture you chose, or its emoji, on a soft glow of its colour. */
@Composable
fun CategoryTile(state: AppState, category: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val cat = state.category(category)
    CategoryTile(cat, state, size, modifier)
}

@Composable
fun CategoryTile(cat: Category?, state: AppState, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val p = Pal
    val tint = if (cat != null) Color(cat.color) else Color(0xFFB0AEC4)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = .45f).compositeOver(p.surface2), tint.copy(alpha = .14f).compositeOver(p.surface2)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = cat?.let { state.imageOf(it) }
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(cat?.emoji ?: "🧾", fontSize = (size.value * 0.46f).sp)
        }
    }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(end = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), color = Pal.muted, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(title, color = Pal.fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Pal.hero).padding(20.dp)) { content() }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Frosted-glass look: a see-through fill and a bright top edge that fades down.
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Pal.surface.copy(alpha = .82f), Pal.surface.copy(alpha = .6f))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Pal.fg.copy(alpha = .22f), Pal.fg.copy(alpha = .05f))), shape)
            .padding(18.dp),
    ) { content() }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String) {
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 42.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Pal.fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Pal.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tint: Color = Pal.accent, onClick: (() -> Unit)? = null) {
    val m = modifier.clip(CircleShape).background(tint.copy(alpha = .16f)).border(1.dp, tint.copy(alpha = .4f), CircleShape)
    Text(
        text, color = Pal.fg, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
        modifier = (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPills(state: AppState, items: List<CategoryTotal>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(6).forEach { o ->
            val cat = state.category(o.category)
            Pill("${cat?.emoji ?: "🧾"}  ${o.category}  ${rupees(o.spentPaise)}", tint = state.colorOf(o.category))
        }
    }
}

/** One spend. Tap, or swipe it to the left, to set its category and comment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnRow(state: AppState, t: Txn, onEdit: () -> Unit) {
    val p = Pal
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) onEdit()
        false // always snap back; the editor opens instead of removing the row
    })
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(p.accent, p.pink))).padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Edit category ›", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        },
    ) {
        val debit = t.type == TxnType.DEBIT
        Row(
            Modifier.fillMaxWidth().background(p.surface).clickable(onClick = onEdit).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryTile(state, t.category)
            Column(Modifier.weight(1f)) {
                Text(t.merchant ?: t.bank ?: "Transaction", color = p.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val time = timeText(t.atMillis)
                    if (time.isNotEmpty()) Text(time, color = p.muted, fontSize = 12.sp)
                    // A small label for which bank account or wallet it belongs to, once there is more than one.
                    state.account(t.accountId)?.takeIf { state.accounts.size > 1 }?.let { Text("· " + it.name, color = p.muted, fontSize = 12.sp, maxLines = 1) }
                    if (t.id in state.repeatPairs) {
                        Text(
                            "Repeat?", color = p.bad, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(CircleShape).border(1.dp, p.bad, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    } else if (t.category.isEmpty()) {
                        Text(
                            "+ add category", color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(CircleShape).border(1.dp, p.accent, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    } else {
                        Text("· ${t.category}", color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (t.comment.isNotEmpty()) Text(t.comment, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                (if (debit) "−" else "+") + rupees(t.amountPaise),
                color = if (debit) p.fg else p.good, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A day (or month) heading that folds its rows away when tapped. */
@Composable
fun FoldGroup(id: String, title: String, sub: String, totalPaise: Long, startOpen: Boolean, content: @Composable () -> Unit) {
    var open by rememberSaveable(id) { mutableStateOf(startOpen) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Pal.fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("  $sub", color = Pal.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(rupees(totalPaise), color = Pal.muted, fontSize = 13.sp)
            Text(if (open) "  ⌄" else "  ›", color = Pal.muted, fontSize = 14.sp)
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

/** Bars on a gradient card: the last (current) bar is solid white, the rest are see-through. */
@Composable
fun HeroBars(bars: List<Bar>, highlight: Int) {
    val max = (bars.maxOfOrNull { it.spentPaise } ?: 0L).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val slot = size.width / bars.size
            val w = minOf(slot * 0.62f, 26.dp.toPx())
            bars.forEachIndexed { i, b ->
                val h = maxOf(4.dp.toPx(), size.height * (b.spentPaise.toFloat() / max))
                drawRoundRect(
                    color = if (i == highlight) Color.White else Color.White.copy(alpha = .38f),
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEachIndexed { i, b ->
                Text(
                    b.label, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp,
                    color = Color.White.copy(alpha = if (i == highlight) 1f else .75f),
                    fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun Donut(state: AppState, items: List<CategoryTotal>, total: Long) {
    val track = Pal.surface2
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            val colors = items.map { state.colorOf(it.category) }
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                items.forEachIndexed { i, o ->
                    val sweep = 360f * o.spentPaise / total
                    drawArc(colors[i], start, maxOf(0f, sweep - 2f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOP", color = Pal.muted, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(items.first().category, color = Pal.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items.take(5).forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(state.colorOf(o.category)))
                    Text("  " + o.category, color = Pal.fg, fontSize = 12.5.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Math.round(100f * o.spentPaise / total)}%", color = Pal.fg, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                }
            }
        }
    }
}
