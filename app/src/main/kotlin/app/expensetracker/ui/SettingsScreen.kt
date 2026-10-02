package app.expensetracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.expensetracker.Notifier
import app.expensetracker.core.TxnType
import app.expensetracker.data.SmsProcessor
import app.expensetracker.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun SettingRow(title: String, sub: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.fg, fontSize = 15.sp)
            Text(sub, color = Pal.muted, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
internal fun SmallButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val p = Pal
    val base = Modifier.clip(RoundedCornerShape(12.dp))
    Text(
        text, color = if (primary) Color.White else p.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = (if (primary) base.background(Brush.horizontalGradient(listOf(p.accent, p.pink))) else base.background(p.surface2).border(1.dp, p.line, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: AppState, onNewCategory: () -> Unit, onReviewNeeds: () -> Unit) {
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
                onSuccess = {
                    "Scanned ${it.scanned} messages and added ${it.added} transactions" +
                        (if (it.skippedOld > 0) ", skipped ${it.skippedOld} from before your start date" else "") +
                        ". Tag them from “Needs a category”."
                },
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
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(toCsv(state.txns).toByteArray()) } }.isSuccess
                }
                status = if (ok) "Saved ${state.txns.size} transactions. Upload the file to Google Drive from your Files app." else "Could not save the file."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Your setup", "Settings")

        Card {
            Column {
                SectionTitle("Monthly budget")
                SettingRow("Budget for each month", "The ring on Today fills against this") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("−") { state.setBudget((state.budgetPaise - 100_000).coerceAtLeast(500_000)) }
                        Text(rupees(state.budgetPaise).removeSuffix(".00"), color = Pal.fg, fontWeight = FontWeight.SemiBold)
                        SmallButton("+") { state.setBudget(state.budgetPaise + 100_000) }
                    }
                }
            }
        }

        BudgetsCard(state)
        ReportsCard(state)
        CurrenciesCard(state)
        AlertsCard()
        SecurityCard()

        Card {
            Column {
                SectionTitle("Tracking from")
                val since = if (state.startDay <= 0) "the beginning" else LocalDate.ofEpochDay(state.startDay).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
                Text(
                    "Counting spends from $since." + if (state.hiddenCount > 0) " ${state.hiddenCount} older ones are hidden, not deleted." else "",
                    color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                var confirm by remember { mutableStateOf(false) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (confirm) "Tap again to hide older" else "Start fresh from today", primary = true) {
                        if (confirm) { state.changeStartDay(LocalDate.now().toEpochDay()); confirm = false } else confirm = true
                    }
                    if (state.startDay > 0) SmallButton("Show older") { state.changeStartDay(0) }
                }
                Text(
                    "Importing old SMS skips anything before this date. Choose “Show older” first if you want your history.",
                    color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Card {
            Column {
                SectionTitle("SMS and data")
                SettingRow("Old messages", "Pull in your past bank SMS once") { SmallButton("Import", primary = true) { if (smsGranted()) runImport() else smsPermission.launch(Manifest.permission.READ_SMS) } }
                SettingRow("Fix double counting", "Remove repeats of the same bank debit") {
                    SmallButton("Fix") {
                        if (!smsGranted()) { smsPermission.launch(Manifest.permission.READ_SMS) } else scope.launch {
                            busy = true
                            status = "Looking for repeated debits…"
                            val n = withContext(Dispatchers.IO) { runCatching { SmsProcessor.cleanDuplicates(context) + app.expensetracker.data.Plans.mergeOldRepeatEntries(context) } }
                            state.refresh()
                            status = n.fold({ if (it == 0) "No repeats found." else "Removed $it repeated ${if (it == 1) "spend" else "spends"}." }, { "Could not check: ${it.message}" })
                            busy = false
                        }
                    }
                }
                SettingRow("Problem report", "Share odd bank messages (numbers masked)") {
                    SmallButton("Share") {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, state.problemReport())
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share problem report"))
                    }
                }
                SettingRow("Needs a category", "${state.needsCategory.size} spends waiting") { SmallButton("Review") { onReviewNeeds() } }
                SettingRow("Alert preview", "Send yourself a sample notification") {
                    SmallButton("Preview") {
                        val t = state.needsCategory.firstOrNull() ?: state.txns.firstOrNull { it.type == TxnType.DEBIT }
                        if (t != null) Notifier.show(context, t) else status = "Add or import a spend first."
                    }
                }
                SettingRow("Spreadsheet", "CSV of your spends") { SmallButton("Export CSV") { exportLauncher.launch("expenses-${LocalDate.now()}.csv") } }
                if (status.isNotEmpty()) Text(status, color = Pal.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        BackupCard(state)

        Card {
            Column {
                SectionTitle("Categories")
                Text("Add your own with an emoji or a picture. Ones you made can be removed.", color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.forEach { c ->
                        Row(
                            Modifier.clip(CircleShape).background(Color(c.color).copy(alpha = .16f)).border(1.dp, Color(c.color).copy(alpha = .4f), CircleShape).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CategoryTile(c, state, size = 24.dp)
                            Text(c.name, color = Pal.fg, fontSize = 13.sp)
                            if (!c.builtin) Text("×", color = Pal.muted, fontSize = 16.sp, modifier = Modifier.clickable { state.deleteCategory(c) })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box { SmallButton("＋ New category", primary = true, onClick = onNewCategory) }
            }
        }

        Card {
            Column {
                SectionTitle("Good to know")
                Text(
                    "Everything stays on this phone. The app has no internet permission, and only bank debit or credit messages are saved.",
                    color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "If SMS permission is greyed out: Settings › Apps › Spendr, tap ⋮ (top right) and choose “Allow restricted settings”, then grant SMS under Permissions.",
                    color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
                )
                Text("Look: follows your phone's light or dark setting.", color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

private fun toCsv(txns: List<Txn>): String {
    fun q(s: String?): String = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
    val sb = StringBuilder("date,type,amount,category,comment,merchant,bank,account,ref,source\n")
    txns.forEach { t ->
        sb.append(LocalDate.ofEpochDay(t.epochDay)).append(',')
            .append(if (t.type == TxnType.DEBIT) "debit" else "credit").append(',')
            .append(app.expensetracker.core.Reports.formatRupees(t.amountPaise).replace(",", "")).append(',')
            .append(q(t.category)).append(',').append(q(t.comment)).append(',')
            .append(q(t.merchant)).append(',').append(q(t.bank)).append(',')
            .append(q(t.account)).append(',').append(q(t.ref)).append(',').append(q(t.source)).append('\n')
    }
    return sb.toString()
}
