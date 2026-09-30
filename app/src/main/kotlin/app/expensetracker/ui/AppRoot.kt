package app.expensetracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.expensetracker.core.Period
import app.expensetracker.data.Txn

private enum class Tab(val label: String, val icon: String) {
    TODAY("Today", "☀️"), WEEK("Week", "📅"), MONTH("Month", "🗓️"), YEAR("Year", "📈"), SETTINGS("Settings", "⚙️"),
}

@Composable
fun AppRoot(openTxnId: Long, onOpenTxnHandled: () -> Unit) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }

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
            tab = Tab.TODAY
            onOpenTxnHandled()
        }
    }

    Scaffold(
        containerColor = Pal.bg,
        bottomBar = {
            NavigationBar(containerColor = Pal.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Pal.accent.copy(alpha = .25f),
                            selectedTextColor = Pal.fg, unselectedTextColor = Pal.muted,
                        ),
                    )
                }
            }
        },
    ) { padding: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(state) { editing = it }
                Tab.WEEK -> PeriodScreen(state, Period.WEEK) { editing = it }
                Tab.MONTH -> PeriodScreen(state, Period.MONTH) { editing = it }
                Tab.YEAR -> PeriodScreen(state, Period.YEAR) { editing = it }
                Tab.SETTINGS -> SettingsScreen(
                    state,
                    onNewCategory = { newCategory = true },
                    onReviewNeeds = { state.needsCategory.firstOrNull()?.let { editing = it } },
                )
            }
            if (tab != Tab.SETTINGS) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Pal.accent, Pal.pink)))
                        .clickable { adding = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋  Add spend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    editing?.let { txn ->
        EditTxnSheet(
            state, txn,
            onSave = { category, comment -> state.save(txn.id, category, comment); editing = null },
            onDelete = { state.delete(txn.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        AddSpendSheet(
            state,
            onAdd = { amount, type, day, category, note -> state.addManual(amount, type, day, category, note); adding = false },
            onDismiss = { adding = false },
        )
    }
    if (newCategory) NewCategorySheet(state) { newCategory = false }
}
