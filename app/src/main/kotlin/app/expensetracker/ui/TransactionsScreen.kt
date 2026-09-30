package app.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun TransactionsScreen(state: AppState, onEdit: (Txn) -> Unit) {
    var onlyUncategorized by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyUncategorized) {
        state.txns.filter { it.category.isEmpty() && it.kind == TxnKind.NORMAL }
    } else state.txns

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyUncategorized, onClick = { onlyUncategorized = false }, label = { Text("All (${state.txns.size})") })
            FilterChip(
                selected = onlyUncategorized,
                onClick = { onlyUncategorized = true },
                label = { Text("Needs category (${state.needsCategoryCount})") },
            )
        }
        if (shown.isEmpty()) {
            Text(
                if (state.txns.isEmpty()) "No transactions yet. Bank and UPI SMS show up here on their own. Use Settings to import your old SMS, or tap + to add one by hand."
                else "Everything has a category.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(shown, key = { it.id }) { t ->
                    TxnRow(t, onClick = { onEdit(t) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TxnRow(t: Txn, onClick: () -> Unit) {
    val debit = t.type == TxnType.DEBIT
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.merchant ?: t.bank ?: "Transaction", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            val sub = buildList {
                add(LocalDate.ofEpochDay(t.epochDay).format(dayFormat))
                add(t.category.ifEmpty { "No category" })
                t.bank?.let { b -> add(b + (t.account?.let { " ••$it" } ?: "")) }
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.comment.isNotEmpty()) Text(t.comment, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            (if (debit) "-" else "+") + "₹" + Reports.formatRupees(t.amountPaise),
            color = if (debit) DebitColor else CreditColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
