package app.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.SmsProcessor
import app.expensetracker.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun smsGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun runImport() {
        scope.launch {
            busy = true
            status = "Reading your SMS inbox…"
            val result = withContext(Dispatchers.IO) { runCatching { SmsProcessor.importInbox(context) } }
            state.refresh()
            status = result.fold(
                onSuccess = { "Scanned ${it.scanned} messages and added ${it.added} transactions. Use “Needs category” on the first tab to tag them." },
                onFailure = { "Import failed: ${it.message}" },
            )
            busy = false
        }
    }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runImport() else status = "SMS permission was not granted, so the old messages can't be read."
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(toCsv(state.txns).toByteArray()) }
                    }.isSuccess
                }
                status = if (ok) "Saved ${state.txns.size} transactions. Upload the file to Google Drive from your Files app." else "Could not save the file."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import old messages", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reads your SMS inbox once and saves every bank / UPI debit and credit. Safe to run again: messages already saved are skipped.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(enabled = !busy, onClick = { if (smsGranted()) runImport() else smsPermission.launch(Manifest.permission.READ_SMS) }) {
            Text("Import SMS inbox")
        }

        Text("Export", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Saves all transactions as a CSV file (opens in Excel or Google Sheets). Pick Google Drive as the location to back it up there.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { exportLauncher.launch("expenses-${LocalDate.now()}.csv") }) { Text("Export CSV") }

        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

        Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Everything is stored only on this phone. The app has no internet permission. Only messages that look like a bank debit or credit are saved; all other SMS are ignored.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("If SMS permission is greyed out", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Android blocks SMS access for apps installed from a file. Open Settings › Apps › Expense Tracker, tap ⋮ (top right) and choose “Allow restricted settings”. Then grant SMS under Permissions.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun toCsv(txns: List<Txn>): String {
    fun q(s: String?): String = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
    val sb = StringBuilder("date,type,amount,category,comment,merchant,bank,account,ref,source\n")
    txns.forEach { t ->
        sb.append(LocalDate.ofEpochDay(t.epochDay)).append(',')
            .append(if (t.type == TxnType.DEBIT) "debit" else "credit").append(',')
            .append(Reports.formatRupees(t.amountPaise).replace(",", "")).append(',')
            .append(q(t.category)).append(',').append(q(t.comment)).append(',')
            .append(q(t.merchant)).append(',').append(q(t.bank)).append(',')
            .append(q(t.account)).append(',').append(q(t.ref)).append(',').append(q(t.source)).append('\n')
    }
    return sb.toString()
}
