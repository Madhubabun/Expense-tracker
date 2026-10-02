package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Account
import app.expensetracker.data.AccountKind
import java.time.LocalDate

/** Wallets: cash, bank accounts, cards. Goals, loans and tags will join this screen. */
@Composable
fun WalletsScreen(state: AppState) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1).toEpochDay()
    val total = state.accounts.filter { it.kind != AccountKind.CARD }.sumOf { state.balanceOf(it) }
    val owed = state.accounts.filter { it.kind == AccountKind.CARD }.sumOf { state.balanceOf(it) }.coerceAtMost(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Where your money sits", "Wallets")

        HeroCard {
            Column {
                Text("TOTAL BALANCE", color = Color.White.copy(alpha = .78f), fontSize = 11.sp, letterSpacing = 1.sp)
                Text(signedRupees(total), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                if (owed < 0) Text("Cards owe ${rupees(-owed).removeSuffix(".00")}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Balances count from your tracking start date, plus the balance you enter for each wallet.",
                    color = Color.White.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionTitle("Your wallets")
        state.accounts.forEach { a ->
            val mine = state.txns.filter { it.accountId == a.id && it.epochDay >= monthStart }
            val out = mine.filter { it.type == TxnType.DEBIT }.sumOf { it.amountPaise }
            val inn = mine.filter { it.type == TxnType.CREDIT }.sumOf { it.amountPaise }
            Card(Modifier.clickable { editing = a }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(Color(a.color).copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                        Text(a.kind.emoji, fontSize = 22.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.name, color = Pal.fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("This month  −${rupees(out).removeSuffix(".00")}  ·  +${rupees(inn).removeSuffix(".00")}", color = Pal.muted, fontSize = 12.sp)
                    }
                    Text(signedRupees(state.balanceOf(a)), color = if (state.balanceOf(a) < 0) Pal.bad else Pal.fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "＋ Add a wallet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Pal.accent.copy(alpha = .9f)).clickable { adding = true }.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }

    if (adding) AccountSheet(state, null) { adding = false }
    editing?.let { AccountSheet(state, it) { editing = null } }
}

fun signedRupees(paise: Long): String = (if (paise < 0) "−" else "") + "₹" + Reports.formatRupees(Math.abs(paise)).removeSuffix(".00")
