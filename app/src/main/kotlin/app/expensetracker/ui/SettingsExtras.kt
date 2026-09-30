package app.expensetracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.data.Backup
import app.expensetracker.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun Context.activity(): Activity? {
    var c = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun FlagRow(title: String, sub: String, key: String, default: Boolean) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Prefs.flag(context, key, default)) }
    SettingRow(title, sub) {
        Switch(checked = on, onCheckedChange = { on = it; Prefs.setFlag(context, key, it) })
    }
}

@Composable
fun AlertsCard() {
    Card {
        Column {
            SectionTitle("Alerts and reminders")
            FlagRow("Budget alerts", "A nudge at 80% and again at 100%", Prefs.BUDGET_ALERTS, true)
            FlagRow("9 pm summary", "Today's total and what still needs a category", Prefs.NIGHTLY_SUMMARY, true)
            FlagRow("Bill reminders", "The day before a monthly bill is due", Prefs.BILL_REMINDERS, true)
        }
    }
}

@Composable
fun SecurityCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Prefs.flag(context, Prefs.APP_LOCK, false)) }
    var note by remember { mutableStateOf("") }
    Card {
        Column {
            SectionTitle("Security")
            SettingRow("App lock", "Fingerprint, face or your phone's screen lock") {
                Switch(checked = on, onCheckedChange = { want ->
                    if (!want) { on = false; Prefs.setFlag(context, Prefs.APP_LOCK, false); note = "" }
                    else if (!Lock.available(context)) note = "Set up a screen lock or fingerprint in your phone's settings first."
                    else {
                        val a = context.activity()
                        if (a == null) note = "Could not open the unlock prompt."
                        else Lock.authenticate(a, onSuccess = { on = true; Prefs.setFlag(context, Prefs.APP_LOCK, true); note = "" }, onFail = { note = "Not turned on. Unlock didn't go through." })
                    }
                })
            }
            if (note.isNotEmpty()) Text(note, color = Pal.accent, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsCard(state: AppState) {
    var picked by remember { mutableStateOf("") }
    var amount by remember { mutableLongStateOf(200_000L) }
    val free = state.pickable.filter { it.name !in state.categoryBudgets }
    Card {
        Column {
            SectionTitle("Category budgets")
            Text("Set a monthly limit for a category. You get an alert at 80% and 100%.", color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            state.categoryBudgets.entries.sortedBy { it.key }.forEach { (name, limit) ->
                SettingRow(name, rupees(limit).removeSuffix(".00") + " a month") { SmallButton("Remove") { state.setCategoryBudget(name, 0) } }
            }
            if (free.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    free.forEach { c ->
                        val sel = picked == c.name
                        Text(
                            c.emoji + " " + c.name, color = Pal.fg, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (sel) Pal.accent.copy(alpha = .3f) else Pal.surface2)
                                .border(1.dp, if (sel) Pal.accent else Pal.line, CircleShape)
                                .clickable { picked = c.name }.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (picked.isNotEmpty()) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("−") { amount = (amount - 50_000).coerceAtLeast(50_000) }
                        Text(rupees(amount).removeSuffix(".00"), color = Pal.fg, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        SmallButton("+") { amount += 50_000 }
                        SmallButton("Set budget", primary = true) { state.setCategoryBudget(picked, amount); picked = "" }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupCard(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Backup.export(context).toByteArray()) } }.isSuccess
            }
            status = if (ok) "Backup saved. Put the file in Google Drive to keep it safe." else "Could not save the backup."
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) confirmRestore = uri
    }

    Card {
        Column {
            SectionTitle("Backup and restore")
            Text(
                "A backup holds everything: spends, categories with pictures, budgets. Restoring replaces what is on this phone now.",
                color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Save backup", primary = true) { save.launch("expense-backup-${LocalDate.now()}.json") }
                SmallButton("Restore") { pick.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
            }
            confirmRestore?.let { uri ->
                Text("This replaces everything on this phone with the backup.", color = Pal.bad, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Yes, restore", primary = true) {
                        confirmRestore = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
                                    .mapCatching { Backup.restore(context, it).getOrThrow() }
                            }
                            state.reloadAll()
                            status = result.fold({ "Restored $it spends." }, { "Restore failed: ${it.message}. Nothing was changed." })
                        }
                    }
                    SmallButton("Cancel") { confirmRestore = null }
                }
            }
            if (status.isNotEmpty()) Text(status, color = Pal.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
