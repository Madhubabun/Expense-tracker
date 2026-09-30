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
import app.expensetracker.data.CategoryImages
import app.expensetracker.data.Category
import app.expensetracker.data.Db
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

    private val images = HashMap<String, Bitmap?>()

    init {
        refresh()
    }

    fun refresh() {
        allTxns = db.all()
        txns = allTxns.filter { it.epochDay >= startDay }
        hiddenCount = allTxns.size - txns.size
        categories = db.categories()
    }

    fun category(name: String): Category? = categories.firstOrNull { it.name == name }

    fun colorOf(name: String): Color = category(name)?.let { Color(it.color) } ?: Color(0xFFB0AEC4)

    fun imageOf(c: Category): Bitmap? = c.image?.let { f -> images.getOrPut(f) { CategoryImages.load(context, f) } }

    /** Categories shown in pickers. "Card bill payment" is set automatically, so it is left out. */
    val pickable: List<Category> get() = categories.filter { it.name != Categories.CARD_BILL }

    fun reportTxns(): List<ReportTxn> = txns.map {
        ReportTxn(it.amountPaise, it.type, LocalDate.ofEpochDay(it.epochDay), it.category, it.kind)
    }

    val needsCategory: List<Txn> get() = txns.filter { it.category.isEmpty() && it.kind == TxnKind.NORMAL }

    fun save(id: Long, category: String, comment: String) {
        db.setCategoryAndComment(id, category, comment)
        refresh()
    }

    fun delete(id: Long) {
        db.delete(id)
        refresh()
    }

    fun addManual(amountPaise: Long, type: TxnType, day: LocalDate, category: String, comment: String) {
        db.insertManual(amountPaise, type, day.toEpochDay(), category, comment, null)
        refresh()
    }

    /** Hides everything before [epochDay]. Pass 0 to show all older transactions again. */
    fun setStartDay(epochDay: Long) {
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
}
