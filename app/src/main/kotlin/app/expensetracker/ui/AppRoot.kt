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
import androidx.compose.foundation.layout.Arrangement
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
    OVERVIEW("Overview", "🏠"), EXPENSES("Expenses", "💸"), INCOME("Income", "💰"), STATS("Statistics", "📊"), PLANS("Plans", "🗂️"),
}

@Composable
fun AppRoot(openTxnId: Long, onOpenTxnHandled: () -> Unit, openAdd: Boolean = false, onOpenAddHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val state = remember { AppState(context) }
    // Pick up changes made from a notification or a new SMS while the app is open, and refresh on coming back.
    androidx.compose.runtime.DisposableEffect(state) {
        val stop = app.expensetracker.data.DataEvents.listen { state.refresh() }
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.refresh()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { stop(); owner?.lifecycle?.removeObserver(observer) }
    }
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    // One period for Overview, Expenses, Income and Statistics, so switching tabs keeps the same view.
    var period by rememberSaveable { mutableStateOf(app.expensetracker.core.Period.MONTH) }
    var offset by rememberSaveable { mutableStateOf(0) }
    val onPeriod: (app.expensetracker.core.Period) -> Unit = { period = it; offset = 0 }
    val onOffset: (Int) -> Unit = { offset = it.coerceAtMost(0) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var insights by remember { mutableStateOf<Pair<Period, java.time.LocalDate>?>(null) }
    var voice by remember { mutableStateOf<app.expensetracker.core.VoiceEntry?>(null) }
    var micNote by remember { mutableStateOf("") }
    val speak = rememberSpeech(onFailed = { micNote = "Couldn't hear that. Try again." }) { said ->
        micNote = ""
        voice = app.expensetracker.core.VoiceParser.parse(said)
        adding = true
    }

    fun startIncome() {
        voice = app.expensetracker.core.VoiceEntry(null, app.expensetracker.core.TxnType.CREDIT, "", "Salary")
        adding = true
    }

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
            tab = Tab.OVERVIEW
            onOpenTxnHandled()
        }
    }

    // Soft colour glows sit behind everything so the glass cards have something to frost.
    val glow = if (Pal.dark) .30f else .16f
    val glowAccent = Pal.accent
    val glowPink = Pal.pink
    LaunchedEffect(openAdd) {
        if (openAdd) { adding = true; onOpenAddHandled() }
    }

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
                Tab.OVERVIEW -> OverviewScreen(state, period, offset, onPeriod, onOffset) { editing = it }
                Tab.EXPENSES -> PeriodScreen(state, period, offset, onPeriod, onOffset, { p, a -> insights = p to a }) { editing = it }
                Tab.INCOME -> IncomeScreen(state, period, offset, onPeriod, onOffset, onAdd = { startIncome() }) { editing = it }
                Tab.STATS -> StatsScreen(state, period, offset, onPeriod, onOffset) { p, a -> insights = p to a }
                Tab.PLANS -> PlansScreen(state)
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp).size(40.dp).clip(CircleShape)
                    .background(Pal.surface2).clickable { settingsOpen = true },
                contentAlignment = Alignment.Center,
            ) { Text("⚙️", fontSize = 18.sp) }
            if (tab != Tab.PLANS) {
                if (micNote.isNotEmpty()) Text(micNote, color = Pal.muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).padding(bottom = 64.dp))
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Pal.surface2).clickable { speak() }, contentAlignment = Alignment.Center) { Text("🎤", fontSize = 19.sp) }
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Pal.surface2).clickable { searching = true }, contentAlignment = Alignment.Center) { Text("🔍", fontSize = 19.sp) }
                    Row(
                        Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(Pal.accent, Pal.pink)))
                            .clickable { if (tab == Tab.INCOME) startIncome() else adding = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (tab == Tab.INCOME) "＋  Add income" else "＋  Add spend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
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
    insights?.let { (p, a) -> InsightsScreen(state, p, a) { insights = null } }
    if (searching) SearchScreen(state, onEdit = { editing = it }, onClose = { searching = false })
    editing?.let { txn ->
        EditTxnSheet(
            state, txn,
            onSave = { category, comment, accountId, tags -> state.save(txn.id, category, comment, accountId, tags); editing = null },
            onDelete = { state.delete(txn.id); editing = null },
            onSplit = { paise, category -> state.splitOff(txn, paise, category).also { if (it) editing = null } },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        AddSpendSheet(
            state,
            onAdd = { n ->
                state.addManual(n.amountPaise, n.type, n.day, n.category, n.note, n.accountId, n.tags, n.currency, n.origPaise)
                adding = false; voice = null
                // Show the period that holds what was just added, so it is not missed on another day.
                offset = offsetFor(period, n.day)
            },
            initial = voice,
            onDismiss = { adding = false; voice = null },
        )
    }
    if (newCategory) NewCategorySheet(state) { newCategory = false }
}

/** How many periods back from now holds [day] (0 for today or the future). */
private fun offsetFor(period: Period, day: java.time.LocalDate): Int {
    val today = java.time.LocalDate.now()
    if (!day.isBefore(today)) return 0
    return when (period) {
        Period.DAY -> -(today.toEpochDay() - day.toEpochDay()).toInt()
        Period.WEEK -> -((today.toEpochDay() - day.toEpochDay()) / 7).toInt()
        Period.MONTH -> -((today.year * 12 + today.monthValue) - (day.year * 12 + day.monthValue))
        Period.YEAR -> -(today.year - day.year)
    }
}
