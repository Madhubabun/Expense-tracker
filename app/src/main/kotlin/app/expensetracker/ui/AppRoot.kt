package app.expensetracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.expensetracker.data.Txn

private val tabs = listOf("Transactions", "Reports", "Settings")

@Composable
fun AppRoot(openTxnId: Long, onOpenTxnHandled: () -> Unit) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())
    }

    // A tap on a notification opens that transaction's editor.
    LaunchedEffect(openTxnId) {
        if (openTxnId >= 0) {
            state.refresh()
            editing = state.txns.firstOrNull { it.id == openTxnId }
            tab = 0
            onOpenTxnHandled()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { adding = true }) { Text("+") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, name -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) }) }
            }
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> TransactionsScreen(state, onEdit = { editing = it })
                    1 -> ReportsScreen(state)
                    else -> SettingsScreen(state)
                }
            }
        }
    }

    editing?.let { txn ->
        EditTxnDialog(
            txn = txn,
            categories = state.categories,
            onSave = { category, comment -> state.save(txn.id, category, comment); editing = null },
            onDelete = { state.delete(txn.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        AddTxnDialog(
            categories = state.categories,
            onAdd = { amount, type, day, category, comment, merchant ->
                state.addManual(amount, type, day, category, comment, merchant)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}
