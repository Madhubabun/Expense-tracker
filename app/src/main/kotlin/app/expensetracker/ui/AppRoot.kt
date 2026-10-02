package app.expensetracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Period
import app.expensetracker.data.Txn

private enum class Tab(val label: String, val icon: String) {
    TODAY("Today", "☀️"), WEEK("Week", "📅"), MONTH("Month", "🗓️"), YEAR("Year", "📈"), WALLETS("Wallets", "👛"),
}

@Composable
fun AppRoot(openTxnId: Long, onOpenTxnHandled: () -> Unit) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

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

    // Soft colour glows sit behind everything so the glass cards have something to frost.
    val glow = if (Pal.dark) .30f else .16f
    val glowAccent = Pal.accent
    val glowPink = Pal.pink
    Scaffold(
        modifier = Modifier.background(Pal.bg).drawBehind {
            drawCircle(
                Brush.radialGradient(listOf(glowAccent.copy(alpha = glow), Color.Transparent), center = Offset(size.width * .95f, size.height * .1f), radius = size.width * .9f),
                radius = size.width * .9f, center = Offset(size.width * .95f, size.height * .1f),
            )
            drawCircle(
                Brush.radialGradient(listOf(glowPink.copy(alpha = glow * .8f), Color.Transparent), center = Offset(size.width * .05f, size.height * .75f), radius = size.width * .8f),
                radius = size.width * .8f, center = Offset(size.width * .05f, size.height * .75f),
            )
        },
        containerColor = Color.Transparent,
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
                Tab.WALLETS -> WalletsScreen(state)
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp).size(40.dp).clip(CircleShape)
                    .background(Pal.surface2).clickable { settingsOpen = true },
                contentAlignment = Alignment.Center,
            ) { Text("⚙️", fontSize = 18.sp) }
            if (tab != Tab.WALLETS) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp).size(48.dp).clip(CircleShape)
                        .background(Pal.surface2).clickable { searching = true },
                    contentAlignment = Alignment.Center,
                ) { Text("🔍", fontSize = 20.sp) }
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

    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        Box(Modifier.fillMaxSize().background(Pal.bg).statusBarsPadding()) {
            SettingsScreen(
                state,
                onNewCategory = { newCategory = true },
                onReviewNeeds = { settingsOpen = false; state.needsCategory.firstOrNull()?.let { editing = it } },
            )
            Text(
                "Close", color = Pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp).clickable { settingsOpen = false },
            )
        }
    }
    if (searching) SearchScreen(state, onEdit = { editing = it }, onClose = { searching = false })
    editing?.let { txn ->
        EditTxnSheet(
            state, txn,
            onSave = { category, comment, accountId -> state.save(txn.id, category, comment, accountId); editing = null },
            onDelete = { state.delete(txn.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        AddSpendSheet(
            state,
            onAdd = { amount, type, day, category, note, accountId -> state.addManual(amount, type, day, category, note, accountId); adding = false },
            onDismiss = { adding = false },
        )
    }
    if (newCategory) NewCategorySheet(state) { newCategory = false }
}
