package app.expensetracker.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expensetracker.core.TxnType
import app.expensetracker.data.Account
import app.expensetracker.data.AccountKind
import app.expensetracker.data.Category
import app.expensetracker.data.Txn
import java.math.BigDecimal
import java.time.LocalDate

internal val EMOJIS = listOf(
    "🍿", "🎮", "✈️", "🏠", "🐶", "📚", "💇", "🏋️", "🎁", "☕", "🍺", "🎧",
    "👶", "🚗", "🔧", "💻", "📱", "🌴", "🧾", "💸", "🎓", "🪴", "🧘", "🍰",
)
internal val SWATCHES = listOf(
    0xFFFF5C7A, 0xFFFF4FD8, 0xFFB18CFF, 0xFF4DA3FF, 0xFF2EF2E0, 0xFFB6FF5C, 0xFFFFB938, 0xFFFF8A4C,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pal.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
internal fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = Pal
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(p.accent, p.pink)) else Brush.horizontalGradient(listOf(p.surface2, p.surface2)))
            .clickable(enabled = enabled, onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Color.White else p.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryPicker(state: AppState, selected: String, onSelect: (String) -> Unit, onNew: () -> Unit) {
    val p = Pal
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.pickable.forEach { c ->
            val on = c.name == selected
            Row(
                Modifier.clip(CircleShape)
                    .background(if (on) p.accent.copy(alpha = .22f) else p.surface2)
                    .border(1.dp, if (on) p.accent else p.line, CircleShape)
                    .clickable { onSelect(c.name) }.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryTile(c, state, size = 24.dp)
                Text(c.name, color = p.fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Text(
            "＋ New category", color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(CircleShape).border(1.dp, p.accent, CircleShape).clickable(onClick = onNew).padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

/** Comma separated tags with one-tap suggestions from tags you already used. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagField(state: AppState, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text("Tags (optional, comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val used = value.split(',').map { it.trim().lowercase().removePrefix("#") }.filter { it.isNotEmpty() }
    val suggestions = state.allTags.filter { it !in used }.take(8)
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { t ->
                Pill("#$t", onClick = { onChange((value.trim().trimEnd(',') + (if (value.isBlank()) "" else ",") + t)) })
            }
        }
    }
}

/** Turns what was typed into the stored form: lowercase, no #, no duplicates. */
fun cleanTags(text: String): String =
    text.split(',').map { it.trim().lowercase().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

/** Pick which wallet a spend belongs to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountPicker(state: AppState, selected: Long, onSelect: (Long) -> Unit) {
    val p = Pal
    Text("Paid from", color = p.muted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { a ->
            val on = a.id == selected
            Text(
                a.kind.emoji + " " + a.name, color = p.fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(CircleShape).background(if (on) Color(a.color).copy(alpha = .28f) else p.surface2)
                    .border(1.dp, if (on) Color(a.color) else p.line, CircleShape).clickable { onSelect(a.id) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun EditTxnSheet(state: AppState, txn: Txn, onSave: (category: String, comment: String, accountId: Long, tags: String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var category by remember(txn.id) { mutableStateOf(txn.category) }
    var comment by remember(txn.id) { mutableStateOf(txn.comment) }
    var accountId by remember(txn.id) { mutableStateOf(txn.accountId) }
    var tags by remember(txn.id) { mutableStateOf(txn.tags) }
    var receipt by remember(txn.id) { mutableStateOf(txn.receipt) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) receipt = state.setReceipt(txn.copy(receipt = receipt), uri)
    }
    var creating by remember { mutableStateOf(false) }
    val sign = if (txn.type == TxnType.DEBIT) "Spent" else "Received"

    AppSheet(onDismiss) {
        Text("$sign ${rupees(txn.amountPaise)}", color = Pal.fg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(txn.merchant ?: txn.bank ?: "Bank SMS", color = Pal.muted)
        CategoryPicker(state, category, { category = it }, { creating = true })
        OutlinedTextField(comment, { comment = it }, label = { Text("Comment") }, modifier = Modifier.fillMaxWidth())
        AccountPicker(state, accountId) { accountId = it }
        TagField(state, tags) { tags = it }
        val shot = receipt?.let { state.receiptImage(it) }
        if (shot != null) {
            Image(shot.asImageBitmap(), contentDescription = "Receipt", Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (shot == null) "📎 Attach receipt" else "Replace receipt", color = Pal.fg)
            }
            if (shot != null) TextButton(onClick = { receipt = state.setReceipt(txn.copy(receipt = receipt), null) }) { Text("Remove", color = Pal.bad) }
        }
        GradientButton("Save") { onSave(category.trim(), comment.trim(), accountId, cleanTags(tags)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onDelete) { Text("Delete", color = Pal.bad) }
            TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.muted) }
        }
    }
    if (creating) NewCategorySheet(state, onDone = { name -> if (name != null) category = name; creating = false })
}

@Composable
fun AddSpendSheet(state: AppState, onAdd: (Long, TxnType, LocalDate, String, String, Long, String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.DEBIT) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(state.cashId) }
    var tags by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    val paise = runCatching { BigDecimal(amount.replace(",", "")).movePointRight(2).toLong() }.getOrNull()
    val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val valid = paise != null && paise > 0 && day != null

    AppSheet(onDismiss) {
        Text("Add a spend", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TxnType.DEBIT to "Spent", TxnType.CREDIT to "Received").forEach { (t, label) ->
                val on = type == t
                Text(
                    label, color = Pal.fg, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(CircleShape).background(if (on) Pal.accent.copy(alpha = .22f) else Pal.surface2)
                        .border(1.dp, if (on) Pal.accent else Pal.line, CircleShape).clickable { type = t }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        OutlinedTextField(
            amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        CategoryPicker(state, category, { category = it }, { creating = true })
        OutlinedTextField(note, { note = it }, label = { Text("What was it? (optional)") }, modifier = Modifier.fillMaxWidth())
        AccountPicker(state, accountId) { accountId = it }
        TagField(state, tags) { tags = it }
        OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = day == null, modifier = Modifier.fillMaxWidth())
        GradientButton("Add spend", enabled = valid) { onAdd(paise!!, type, day!!, category, note.trim(), accountId, cleanTags(tags)) }
    }
    if (creating) NewCategorySheet(state, onDone = { name -> if (name != null) category = name; creating = false })
}

/** Make a category with its own emoji or a picture from your gallery. [onDone] gets the new name, or null if cancelled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategorySheet(state: AppState, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🍿") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var color by remember { mutableStateOf(SWATCHES[2]) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) image = uri }
    val preview = remember(image) {
        image?.let { u ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()
        }
    }

    AppSheet(onDismiss = { onDone(null) }) {
        Text("New category", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(19.dp)).background(Color(color).copy(alpha = .3f)), contentAlignment = Alignment.Center) {
                if (preview != null) Image(preview.asImageBitmap(), null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                else Text(emoji, fontSize = 28.sp)
            }
            OutlinedTextField(
                name, { name = it.take(18); error = "" }, label = { Text("Name, e.g. Pets") }, singleLine = true,
                isError = error.isNotEmpty(), supportingText = { if (error.isNotEmpty()) Text(error) }, modifier = Modifier.weight(1f),
            )
        }
        Text("PICK AN EMOJI", color = Pal.muted, fontSize = 11.sp, letterSpacing = 1.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EMOJIS.forEach { e ->
                val on = image == null && emoji == e
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Pal.accent.copy(alpha = .25f) else Pal.surface2)
                        .border(1.dp, if (on) Pal.accent else Pal.line, RoundedCornerShape(12.dp)).clickable { emoji = e; image = null },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 20.sp) }
            }
        }
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (image == null) "Or choose a picture" else "Change picture")
        }
        Text("COLOUR", color = Pal.muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SWATCHES.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(2.dp, if (color == c) Pal.fg else Color.Transparent, CircleShape).clickable { color = c },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        GradientButton("Create category") {
            val n = name.trim()
            when {
                n.isEmpty() -> error = "Give it a name"
                !state.addCategory(n, emoji, color, image) -> error = "That category already exists"
                else -> onDone(n)
            }
        }
        TextButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = Pal.muted) }
    }
}

/** Add a wallet, or edit one. Cash can be renamed but not deleted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSheet(state: AppState, editing: Account?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var kind by remember { mutableStateOf(editing?.kind ?: AccountKind.BANK) }
    var opening by remember {
        mutableStateOf(editing?.openingPaise?.let { if (it == 0L) "" else BigDecimal(it).movePointLeft(2).stripTrailingZeros().toPlainString() } ?: "")
    }
    var color by remember { mutableStateOf(editing?.color ?: SWATCHES[3]) }
    var confirmDelete by remember { mutableStateOf(false) }
    val paise = if (opening.isBlank()) 0L else runCatching { BigDecimal(opening.replace(",", "")).movePointRight(2).toLong() }.getOrNull()

    AppSheet(onDone) {
        Text(if (editing == null) "New wallet" else "Edit wallet", color = Pal.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, { name = it }, label = { Text("Name (for example HDFC savings)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountKind.entries.forEach { k ->
                val on = kind == k
                Text(
                    k.emoji + " " + k.label, color = Pal.fg, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(CircleShape).background(if (on) Pal.accent.copy(alpha = .22f) else Pal.surface2)
                        .border(1.dp, if (on) Pal.accent else Pal.line, CircleShape).clickable { kind = k }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        OutlinedTextField(
            opening, { opening = it }, label = { Text("Balance today (₹, optional)") }, singleLine = true, isError = paise == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SWATCHES.forEach { c ->
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, Pal.fg, CircleShape).clickable { color = c },
                )
            }
        }
        GradientButton(if (editing == null) "Add wallet" else "Save", enabled = name.isNotBlank() && paise != null) {
            if (editing == null) state.addAccount(name, kind, paise!!, color) else state.updateAccount(editing, name, kind, paise!!, color)
            onDone()
        }
        if (editing != null && editing.kind != AccountKind.CASH) {
            TextButton(onClick = { if (confirmDelete) { state.deleteAccount(editing); onDone() } else confirmDelete = true }) {
                Text(if (confirmDelete) "Tap again: spends stay, just unassigned" else "Delete wallet", color = Pal.bad)
            }
        }
    }
}
