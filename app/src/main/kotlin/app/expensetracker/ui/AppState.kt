package app.expensetracker.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.expensetracker.core.ReportTxn
import app.expensetracker.data.Db
import app.expensetracker.data.Txn
import java.time.LocalDate

/** Holds what the screens show. Call [refresh] after any change to the database. */
class AppState(context: Context) {
    private val db = Db.get(context)

    var txns by mutableStateOf<List<Txn>>(emptyList())
        private set
    var categories by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        refresh()
    }

    fun refresh() {
        txns = db.all()
        categories = (db.categoriesInUse() + app.expensetracker.core.Categories.defaults).distinct()
    }

    fun reportTxns(): List<ReportTxn> = txns.map {
        ReportTxn(it.amountPaise, it.type, LocalDate.ofEpochDay(it.epochDay), it.category, it.kind)
    }

    val needsCategoryCount: Int get() = txns.count { it.category.isEmpty() && it.kind == app.expensetracker.core.TxnKind.NORMAL }

    fun save(id: Long, category: String, comment: String) {
        db.setCategoryAndComment(id, category, comment)
        refresh()
    }

    fun delete(id: Long) {
        db.delete(id)
        refresh()
    }

    fun addManual(amountPaise: Long, type: app.expensetracker.core.TxnType, day: LocalDate, category: String, comment: String, merchant: String?) {
        db.insertManual(amountPaise, type, day.toEpochDay(), category, comment, merchant)
        refresh()
    }
}
