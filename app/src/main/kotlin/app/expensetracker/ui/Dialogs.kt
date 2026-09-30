package app.expensetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c ->
            FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c) })
        }
    }
    OutlinedTextField(
        value = selected,
        onValueChange = onSelect,
        label = { Text("Category (pick or type your own)") },
        singleLine = true,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun EditTxnDialog(
    txn: Txn,
    categories: List<String>,
    onSave: (category: String, comment: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember(txn.id) { mutableStateOf(txn.category) }
    var comment by remember(txn.id) { mutableStateOf(txn.comment) }
    val sign = if (txn.type == TxnType.DEBIT) "Spent" else "Received"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$sign ₹${Reports.formatRupees(txn.amountPaise)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                txn.merchant?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                CategoryPicker(categories, category) { category = it.trim() }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(category.trim(), comment.trim()) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun AddTxnDialog(
    categories: List<String>,
    onAdd: (amountPaise: Long, type: TxnType, day: LocalDate, category: String, comment: String, merchant: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.DEBIT) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    val paise = runCatching { BigDecimal(amount.replace(",", "")).movePointRight(2).toLong() }.getOrNull()
    val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val valid = paise != null && paise > 0 && day != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add by hand") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == TxnType.DEBIT, onClick = { type = TxnType.DEBIT }, label = { Text("Spent") })
                    FilterChip(selected = type == TxnType.CREDIT, onClick = { type = TxnType.CREDIT }, label = { Text("Received") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = day == null,
                )
                CategoryPicker(categories, category) { category = it }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAdd(paise!!, type, day!!, category.trim(), comment.trim(), null) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
