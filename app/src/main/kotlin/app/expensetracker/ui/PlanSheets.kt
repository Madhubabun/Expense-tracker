package app.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.TxnType
import app.expensetracker.data.Goal
import app.expensetracker.data.Loan
import app.expensetracker.data.Repeat
import java.math.BigDecimal
import java.time.LocalDate

/** "1,500.50" to paise. Null when it is not a number. Blank counts as zero only when [blankIsZero]. */
fun parseRupees(text: String, blankIsZero: Boolean = false): Long? =
    if (text.isBlank()) (if (blankIsZero) 0L else null)
    else runCatching { BigDecimal(text.replace(",", "").trim()).movePointRight(2).toLong() }.getOrNull()

private fun plain(paise: Long): String = if (paise == 0L) "" else BigDecimal(paise).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun parseDay(text: String): Long? =
    if (text.isBlank()) 0L else runCatching { LocalDate.parse(text.trim()).toEpochDay() }.getOrNull()

private fun dayText(epoch: Long): String = if (epoch <= 0) "" else LocalDate.ofEpochDay(epoch).toString()

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = Pal.fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(CircleShape).background(if (on) Pal.accent.copy(alpha = .22f) else Pal.surface2)
            .border(1.dp, if (on) Pal.accent else Pal.line, CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** One amount box, for "add to this goal" or "record a payment". */
@Composable
fun AmountSheet(title: String, action: String, onAmount: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val paise = parseRupees(text)
    AppSheet(onDismiss) {
        Text(title, color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            text, { text = it }, label = { Text("Amount (₹)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        GradientButton(action, enabled = paise != null && paise > 0) { onAmount(paise!!); onDismiss() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSheet(state: AppState, editing: Goal?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var emoji by remember { mutableStateOf(editing?.emoji ?: "🎯") }
    var savings by remember { mutableStateOf(editing?.savings ?: true) }
    var target by remember { mutableStateOf(plain(editing?.targetPaise ?: 0)) }
    var saved by remember { mutableStateOf(plain(editing?.savedPaise ?: 0)) }
    var end by remember { mutableStateOf(dayText(editing?.endDay ?: 0)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = parseRupees(target)
    val sv = parseRupees(saved, blankIsZero = true)
    val e = parseDay(end)

    AppSheet(onDone) {
        Text(if (editing == null) "New goal" else "Edit goal", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Goal (for example Vacation)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("🎯", "🏖️", "🚗", "🏠", "💍", "📱", "🎓", "⛵") + EMOJIS.take(8)).distinct().forEach { em ->
                Choice(em, emoji == em) { emoji = em }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Save up", savings) { savings = true }
            Choice("Spending limit", !savings) { savings = false }
        }
        OutlinedTextField(
            target, { target = it }, label = { Text("Target (₹)") }, singleLine = true, isError = target.isNotBlank() && t == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            saved, { saved = it }, label = { Text(if (savings) "Saved so far (₹)" else "Spent so far (₹)") }, singleLine = true,
            isError = sv == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(end, { end = it }, label = { Text("Reach it by (YYYY-MM-DD, optional)") }, singleLine = true, isError = e == null, modifier = Modifier.fillMaxWidth())
        GradientButton("Save", enabled = name.isNotBlank() && t != null && t > 0 && sv != null && e != null) {
            state.saveGoal(editing, name, emoji, savings, t!!, sv!!, e!!); onDone()
        }
        if (editing != null) TextButton(onClick = { if (confirmDelete) { state.deleteGoal(editing); onDone() } else confirmDelete = true }) {
            Text(if (confirmDelete) "Tap again to delete" else "Delete goal", color = Pal.bad)
        }
    }
}

@Composable
fun LoanSheet(state: AppState, editing: Loan?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var borrowed by remember { mutableStateOf(editing?.borrowed ?: true) }
    var total by remember { mutableStateOf(plain(editing?.totalPaise ?: 0)) }
    var paid by remember { mutableStateOf(plain(editing?.paidPaise ?: 0)) }
    var due by remember { mutableStateOf(dayText(editing?.dueDay ?: 0)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = parseRupees(total)
    val p = parseRupees(paid, blankIsZero = true)
    val d = parseDay(due)

    AppSheet(onDone) {
        Text(if (editing == null) "New loan" else "Edit loan", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Name (car loan, a friend…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("I owe", borrowed) { borrowed = true }
            Choice("I lent", !borrowed) { borrowed = false }
        }
        OutlinedTextField(
            total, { total = it }, label = { Text("Total amount (₹)") }, singleLine = true, isError = total.isNotBlank() && t == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            paid, { paid = it }, label = { Text(if (borrowed) "Paid back so far (₹)" else "Received back so far (₹)") }, singleLine = true,
            isError = p == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(due, { due = it }, label = { Text("Due date (YYYY-MM-DD, optional)") }, singleLine = true, isError = d == null, modifier = Modifier.fillMaxWidth())
        GradientButton("Save", enabled = name.isNotBlank() && t != null && t > 0 && p != null && d != null) {
            state.saveLoan(editing, name, borrowed, t!!, p!!, d!!); onDone()
        }
        if (editing != null) TextButton(onClick = { if (confirmDelete) { state.deleteLoan(editing); onDone() } else confirmDelete = true }) {
            Text(if (confirmDelete) "Tap again to delete" else "Delete loan", color = Pal.bad)
        }
    }
}

val RepeatTypes = listOf("EMI", "Loan", "Mutual fund", "Rent", "Insurance", "Tithe", "Salary", "Other")

fun repeatEmoji(rtype: String) = when (rtype) {
    "EMI" -> "🏦"; "Loan" -> "🤝"; "Mutual fund" -> "📈"; "Rent" -> "🏡"; "Insurance" -> "🛡️"; "Tithe" -> "🙏"; "Salary" -> "💰"; else -> "🔁"
}

private fun defaultCategory(rtype: String) = when (rtype) {
    "EMI", "Loan", "Rent", "Insurance" -> "Bills"
    "Mutual fund" -> app.expensetracker.core.Categories.INVEST
    "Salary" -> "Salary"
    else -> "Other"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatSheet(state: AppState, editing: Repeat?, onDone: () -> Unit) {
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var rtype by remember { mutableStateOf(editing?.rtype ?: "EMI") }
    var amount by remember { mutableStateOf(plain(editing?.amountPaise ?: 0)) }
    var percent by remember { mutableStateOf(if ((editing?.pct ?: 0) > 0) editing!!.pct.toString() else "10") }
    var category by remember { mutableStateOf(editing?.category ?: defaultCategory(rtype)) }
    var day by remember { mutableStateOf(((editing?.dayOfMonth ?: LocalDate.now().dayOfMonth).takeIf { it <= 31 } ?: 1).toString()) }
    var lastWorking by remember { mutableStateOf(editing?.dayOfMonth == app.expensetracker.core.RepeatPlan.LAST_WORKING_DAY || (editing == null && false)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val paise = parseRupees(amount)
    val pct = percent.trim().toIntOrNull()?.takeIf { it in 1..100 }
    val dom = if (lastWorking) app.expensetracker.core.RepeatPlan.LAST_WORKING_DAY else day.trim().toIntOrNull()?.takeIf { it in 1..31 }
    val tithe = rtype == "Tithe"
    val type = if (rtype == "Salary") TxnType.CREDIT else TxnType.DEBIT

    AppSheet(onDone) {
        Text(if (editing == null) "New monthly repeat" else "Edit repeat", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatTypes.forEach { t ->
                Choice("${repeatEmoji(t)} $t", rtype == t) {
                    rtype = t
                    if (t == "Salary") lastWorking = true
                    category = defaultCategory(t)
                    if (title.isBlank() || title in RepeatTypes) title = if (t == "Other") "" else t
                }
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("Name (Home loan, Groww SIP, Rent…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (tithe) {
            OutlinedTextField(
                percent, { percent = it }, label = { Text("Percent of your monthly income") }, singleLine = true, isError = pct == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                amount, { amount = it }, label = { Text(if (type == TxnType.CREDIT) "Expected amount (₹)" else "Amount (₹)") }, singleLine = true, isError = amount.isNotBlank() && paise == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("On a fixed day", !lastWorking) { lastWorking = false }
            Choice("Last working day", lastWorking) { lastWorking = true }
        }
        if (!lastWorking) OutlinedTextField(
            day, { day = it }, label = { Text("Day of the month (1–31)") }, singleLine = true, isError = dom == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        else Text("Falls on the last Monday to Friday of each month, like a salary.", color = Pal.muted, fontSize = 12.sp)
        CategoryPicker(state, category, { category = it }, { creating = true })
        Text(
            "You get a reminder at 9 am the day before and on the day. When the bank message arrives it is matched to this repeat and counted once. Short months use their last day.",
            color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
        )
        GradientButton("Save", enabled = title.isNotBlank() && dom != null && (if (tithe) pct != null else (paise != null && paise > 0))) {
            state.saveRepeat(editing, title, if (tithe) 0 else paise!!, type, category, editing?.accountId ?: 0, dom!!, rtype, if (tithe) pct!! else 0); onDone()
        }
        if (editing != null) TextButton(onClick = { if (confirmDelete) { state.deleteRepeat(editing); onDone() } else confirmDelete = true }) {
            Text(if (confirmDelete) "Tap again to delete" else "Delete repeat", color = Pal.bad)
        }
    }
    if (creating) NewCategorySheet(state, onDone = { n -> if (n != null) category = n; creating = false })
}

@Composable
fun InvestSheet(state: AppState, editing: app.expensetracker.data.Investment?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var invested by remember { mutableStateOf(plain(editing?.investedPaise ?: 0)) }
    var value by remember { mutableStateOf(plain(editing?.valuePaise ?: 0)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val inPaise = parseRupees(invested, blankIsZero = true)
    val valPaise = parseRupees(value, blankIsZero = true)
    AppSheet(onDone) {
        Text(if (editing == null) "New investment" else "Edit investment", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Name (Groww SIP, Gold, FD…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            invested, { invested = it }, label = { Text("Invested so far (₹)") }, singleLine = true, isError = inPaise == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value, { value = it }, label = { Text("Worth today (₹, optional)") }, singleLine = true, isError = valPaise == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        GradientButton("Save", enabled = name.isNotBlank() && inPaise != null && valPaise != null) {
            state.saveInvestment(editing, name, inPaise!!, valPaise!!); onDone()
        }
        if (editing != null) TextButton(onClick = { if (confirmDelete) { state.deleteInvestment(editing); onDone() } else confirmDelete = true }) {
            Text(if (confirmDelete) "Tap again to delete" else "Delete", color = Pal.bad)
        }
    }
}

/** Type this pay cycle's income yourself. It replaces what the app worked out from your bank messages. */
@Composable
fun IncomeSheet(state: AppState, onDone: () -> Unit) {
    var text by remember { mutableStateOf(plain(state.cycleIncome())) }
    val paise = parseRupees(text)
    AppSheet(onDone) {
        Text("Income until payday", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Type what you have to live on until your next salary. Safe to spend and tithe use it.", color = Pal.muted, fontSize = 13.sp)
        OutlinedTextField(
            text, { text = it }, label = { Text("Income (₹)") }, singleLine = true, isError = text.isNotBlank() && paise == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        GradientButton("Save", enabled = paise != null && paise > 0) { state.setCycleIncome(paise!!); onDone() }
        if (state.incomeTyped) TextButton(onClick = { state.setCycleIncome(0); onDone() }) { Text("Use what the app detected", color = Pal.accent) }
    }
}
