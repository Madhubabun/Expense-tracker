package app.expensetracker.data

import android.content.Context
import app.expensetracker.core.Period
import app.expensetracker.core.ReportTxn
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import java.time.LocalDate

/** Numbers computed straight from the database, for places with no screen: the widget, alerts, reminders. */
object Snapshot {
    data class Today(val spentTodayPaise: Long, val monthSpentPaise: Long, val budgetPaise: Long, val needCategory: Int)

    /** Spends from the tracking start day onwards. */
    fun visible(context: Context): List<Txn> {
        val start = Prefs.startDay(context)
        return Db.get(context).all().filter { it.epochDay >= start }
    }

    fun report(txns: List<Txn>): List<ReportTxn> =
        txns.map { ReportTxn(it.amountPaise, it.type, LocalDate.ofEpochDay(it.epochDay), it.category, it.kind) }

    fun today(context: Context): Today {
        val txns = visible(context)
        val rt = report(txns)
        val today = LocalDate.now()
        val spentToday = rt.filter { it.countable && it.type == TxnType.DEBIT && it.date == today }.sumOf { it.amountPaise }
        val month = Reports.summarize(rt, Period.MONTH, today).spentPaise
        val need = txns.count { it.category.isEmpty() && it.kind == TxnKind.NORMAL }
        return Today(spentToday, month, Prefs.budgetPaise(context), need)
    }
}
