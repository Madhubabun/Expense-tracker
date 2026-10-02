package app.expensetracker.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.expensetracker.core.Categories
import app.expensetracker.core.ReportTxn
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.data.Account
import app.expensetracker.data.AccountKind
import app.expensetracker.data.CategoryImages
import app.expensetracker.data.Category
import app.expensetracker.data.Db
import app.expensetracker.data.Goal
import app.expensetracker.data.Loan
import app.expensetracker.data.Plans
import app.expensetracker.data.Receipts
import app.expensetracker.data.Repeat
import app.expensetracker.data.Prefs
import app.expensetracker.data.Txn
import java.time.LocalDate

/** Holds what the screens show. Call [refresh] after any change to the database. */
class AppState(private val context: Context) {
    private val db = Db.get(context)

    private var allTxns = emptyList<Txn>()

    /** Spends from the tracking start day onwards. Older ones stay in the database but are hidden. */
    var txns by mutableStateOf<List<Txn>>(emptyList())
        private set
    var startDay by mutableStateOf(Prefs.startDay(context))
        private set
    var hiddenCount by mutableStateOf(0)
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var budgetPaise by mutableStateOf(Prefs.budgetPaise(context))
        private set

    /** Monthly limit per category, in paise. */
    var categoryBudgets by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set

    var goals by mutableStateOf<List<Goal>>(emptyList())
        private set
    var loans by mutableStateOf<List<Loan>>(emptyList())
        private set
    var repeats by mutableStateOf<List<Repeat>>(emptyList())
        private set

    private val images = HashMap<String, Bitmap?>()

    init {
        runCatching { Plans.applyRepeats(context) }
        refresh()
    }

    fun refresh() {
        allTxns = db.all()
        txns = allTxns.filter { it.epochDay >= startDay }
        hiddenCount = allTxns.size - txns.size
        categories = db.categories()
        categoryBudgets = db.categoryBudgets()
        accounts = db.accounts()
        goals = Plans.goals(context)
        loans = Plans.loans(context)
        repeats = Plans.repeats(context)
        runCatching { app.expensetracker.TodayWidget.refresh(context) }
    }

    /** Call after a restore: everything on disk changed underneath us. */
    fun reloadAll() {
        images.clear()
        runCatching { Plans.applyRepeats(context) }
        startDay = Prefs.startDay(context)
        budgetPaise = Prefs.budgetPaise(context)
        refresh()
    }

    fun setCategoryBudget(category: String, paise: Long) {
        db.setCategoryBudget(category, paise)
        categoryBudgets = db.categoryBudgets()
    }

    /** Monthly bills spotted from all history, hidden spends included. Soonest first. */
    fun bills() = app.expensetracker.Alerts.detectBills(context, LocalDate.now().toEpochDay()).sortedBy { it.nextDay }

    fun category(name: String): Category? = categories.firstOrNull { it.name == name }

    fun colorOf(name: String): Color = category(name)?.let { Color(it.color) } ?: Color(0xFFB0AEC4)

    fun imageOf(c: Category): Bitmap? = c.image?.let { f -> images.getOrPut(f) { CategoryImages.load(context, f) } }

    /** Categories shown in pickers. "Card bill payment" is set automatically, so it is left out. */
    val pickable: List<Category> get() = categories.filter { it.name != Categories.CARD_BILL }

    fun reportTxns(): List<ReportTxn> = txns.map {
        ReportTxn(it.amountPaise, it.type, LocalDate.ofEpochDay(it.epochDay), it.category, it.kind)
    }

    val needsCategory: List<Txn> get() = txns.filter { it.category.isEmpty() && it.kind == TxnKind.NORMAL }

    fun save(id: Long, category: String, comment: String, accountId: Long? = null, tags: String? = null) {
        db.setCategoryAndComment(id, category, comment)
        if (accountId != null) db.setTxnAccount(id, accountId)
        if (tags != null) db.setTags(id, tags)
        refresh()
        runCatching { app.expensetracker.Alerts.checkBudgets(context) }
    }

    fun delete(id: Long) {
        db.delete(id)
        refresh()
    }

    fun addManual(amountPaise: Long, type: TxnType, day: LocalDate, category: String, comment: String, accountId: Long = db.cashAccountId(), tags: String = "") {
        db.insertManual(amountPaise, type, day.toEpochDay(), category, comment, null, accountId, tags)
        refresh()
        runCatching { app.expensetracker.Alerts.checkBudgets(context) }
    }

    /** Hides everything before [epochDay]. Pass 0 to show all older transactions again. */
    fun changeStartDay(epochDay: Long) {
        startDay = epochDay
        Prefs.setStartDay(context, epochDay)
        refresh()
    }

    fun setBudget(paise: Long) {
        budgetPaise = paise
        Prefs.setBudgetPaise(context, paise)
    }

    /** Returns false if the name is taken. [imageUri] is a picture to use instead of the emoji. */
    fun addCategory(name: String, emoji: String, color: Long, imageUri: android.net.Uri?): Boolean {
        val file = imageUri?.let { CategoryImages.save(context, it) }
        val ok = db.addCategory(name.trim(), emoji, color, file)
        if (!ok) CategoryImages.delete(context, file)
        refresh()
        return ok
    }

    fun deleteCategory(c: Category) {
        db.deleteCategory(c.name)
        CategoryImages.delete(context, c.image)
        refresh()
    }

    fun account(id: Long): Account? = accounts.firstOrNull { it.id == id }

    /** Opening balance plus everything received minus everything spent in that wallet since tracking started. */
    fun balanceOf(a: Account): Long =
        a.openingPaise + txns.filter { it.accountId == a.id }.sumOf { if (it.type == TxnType.CREDIT) it.amountPaise else -it.amountPaise }

    fun addAccount(name: String, kind: AccountKind, openingPaise: Long, color: Long) {
        db.addAccount(name.trim(), kind, openingPaise, color)
        refresh()
    }

    fun updateAccount(a: Account, name: String, kind: AccountKind, openingPaise: Long, color: Long) {
        db.updateAccount(a.id, name.trim(), kind, openingPaise, color)
        refresh()
    }

    fun deleteAccount(a: Account) {
        db.deleteAccount(a.id)
        refresh()
    }

    val cashId: Long get() = accounts.firstOrNull { it.kind == AccountKind.CASH }?.id ?: 0L

    // Tags

    fun tagsOf(t: Txn): List<String> = t.tags.split(',').filter { it.isNotBlank() }

    val allTags: List<String> get() = txns.flatMap { tagsOf(it) }.distinct().sorted()

    // Receipts

    private val receiptCache = HashMap<String, Bitmap?>()

    fun receiptImage(file: String): Bitmap? = receiptCache.getOrPut(file) { Receipts.load(context, file) }

    /** Replaces the receipt of a spend with the picture at [uri], or removes it when [uri] is null. Returns the new file name. */
    fun setReceipt(t: Txn, uri: android.net.Uri?): String? {
        val file = uri?.let { Receipts.save(context, it) }
        if (uri != null && file == null) return t.receipt
        t.receipt?.let { receiptCache.remove(it); Receipts.delete(context, it) }
        db.setReceipt(t.id, file)
        refresh()
        return file
    }

    // Goals, loans and repeating transactions

    fun saveGoal(g: Goal?, name: String, emoji: String, savings: Boolean, target: Long, saved: Long, endDay: Long) {
        Plans.saveGoal(context, g?.id, name.trim(), emoji, savings, target, saved, endDay); refresh()
    }

    fun addToGoal(g: Goal, paise: Long) { Plans.addToGoal(context, g.id, paise); refresh() }

    fun deleteGoal(g: Goal) { Plans.deleteGoal(context, g.id); refresh() }

    fun saveLoan(l: Loan?, name: String, borrowed: Boolean, total: Long, paid: Long, dueDay: Long) {
        Plans.saveLoan(context, l?.id, name.trim(), borrowed, total, paid, dueDay); refresh()
    }

    fun addToLoan(l: Loan, paise: Long) { Plans.addToLoan(context, l.id, paise); refresh() }

    fun deleteLoan(l: Loan) { Plans.deleteLoan(context, l.id); refresh() }

    fun saveRepeat(r: Repeat?, title: String, amount: Long, type: TxnType, category: String, accountId: Long, day: Int) {
        Plans.saveRepeat(context, r?.id, title.trim(), amount, type, category, accountId, day)
        Plans.applyRepeats(context)
        refresh()
    }

    fun deleteRepeat(r: Repeat) { Plans.deleteRepeat(context, r.id); refresh() }
}
