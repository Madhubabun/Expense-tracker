package app.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn

private enum class Kind(val label: String) { ANY("All"), SPENT("Spent"), RECEIVED("Received"), UNTAGGED("No category") }

/** Full-screen search over everything that is visible, with a few filters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: AppState, onEdit: (Txn) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(Kind.ANY) }
    var category by remember { mutableStateOf<String?>(null) }
    var bank by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val tokens = query.lowercase().split(' ').filter { it.isNotBlank() }
    val banks = state.txns.mapNotNull { it.bank }.distinct().sorted()
    val usedCategories = state.txns.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted()

    val results = state.txns.filter { t ->
        val okKind = when (kind) {
            Kind.ANY -> true
            Kind.SPENT -> t.type == TxnType.DEBIT
            Kind.RECEIVED -> t.type == TxnType.CREDIT
            Kind.UNTAGGED -> t.category.isEmpty()
        }
        val haystack = listOfNotNull(t.merchant, t.comment, t.category, t.bank, t.account, t.ref, t.tags.replace(",", " "), rupees(t.amountPaise).replace(",", "").removeSuffix(".00"))
            .joinToString(" ").lowercase()
        okKind && (category == null || t.category == category) && (bank == null || t.bank == bank) &&
            tokens.all { it.replace(",", "").removePrefix("₹") in haystack }
    }
    val total = results.filter { it.type == TxnType.DEBIT }.sumOf { it.amountPaise }

    Column(Modifier.fillMaxSize().background(Pal.bg).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Pal.surface2).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (query.isEmpty()) Text("Search shop, note, bank or amount", color = Pal.muted, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Pal.fg, fontSize = 15.sp), cursorBrush = SolidColor(Pal.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Text("Close", color = Pal.accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }

        FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kind.entries.forEach { k -> Chip(k.label, kind == k) { kind = k } }
            usedCategories.forEach { c -> Chip(c, category == c) { category = if (category == c) null else c } }
            banks.forEach { b -> Chip(b, bank == b) { bank = if (bank == b) null else b } }
        }

        Text(
            "${results.size} ${if (results.size == 1) "result" else "results"}" + if (total > 0) " · ${rupees(total).removeSuffix(".00")} spent" else "",
            color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp),
        )

        if (results.isEmpty()) {
            EmptyState("🕵️", "Nothing found", "Try fewer words, or clear a filter.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { t -> TxnRow(state, t) { onEdit(t) } }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, color = Pal.fg, fontSize = 13.sp,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) Pal.accent.copy(alpha = .3f) else Pal.surface2)
            .border(1.dp, if (selected) Pal.accent else Pal.line, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
