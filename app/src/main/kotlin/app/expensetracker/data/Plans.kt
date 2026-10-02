package app.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import java.time.LocalDate
import java.time.YearMonth

data class Goal(
    val id: Long,
    val name: String,
    val emoji: String,
    /** True for "save up for" goals, false for "spend on" goals such as a trip budget. */
    val savings: Boolean,
    val targetPaise: Long,
    val savedPaise: Long,
    /** Last day to reach it, or 0 for no deadline. */
    val endDay: Long,
)

/** Something you invested in: what you put in and what it is worth now. [valuePaise] 0 means not entered. */
data class Investment(val id: Long, val name: String, val investedPaise: Long, val valuePaise: Long)

data class Loan(
    val id: Long,
    val name: String,
    /** True when you owe it, false when you lent it. */
    val borrowed: Boolean,
    val totalPaise: Long,
    val paidPaise: Long,
    /** Due day, or 0 for none. */
    val dueDay: Long,
) {
    val leftPaise: Long get() = (totalPaise - paidPaise).coerceAtLeast(0)
}

/** A spend or income that repeats every month on [dayOfMonth] (rent, EMI, SIP, salary). */
data class Repeat(
    val id: Long,
    val title: String,
    val amountPaise: Long,
    val type: TxnType,
    val category: String,
    val accountId: Long,
    val dayOfMonth: Int,
    val startDay: Long,
    val lastDay: Long,
    /** EMI, Loan, Mutual fund, Rent, Insurance, Tithe, Salary or Other. */
    val rtype: String = "Other",
    /** For tithe-style repeats: this percent of the month's income instead of a fixed amount. 0 for fixed. */
    val pct: Int = 0,
    /** Year and month (202610) in which the bank debit or credit for this repeat was seen. 0 if not yet. */
    val paidMonth: Int = 0,
)

/** One repeat's turn in a month. [paid] is true once its bank debit or credit has arrived. */
data class RepeatDue(val repeat: Repeat, val day: LocalDate, val amountPaise: Long, val paid: Boolean) {
    val overdue: Boolean get() = !paid && day.isBefore(LocalDate.now())
}

/** Goals, loans and repeating transactions. Stored in the same SQLite file as everything else. */
object Plans {
    fun createTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE goal (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT NOT NULL,
                savings INTEGER NOT NULL, target_paise INTEGER NOT NULL, saved_paise INTEGER NOT NULL DEFAULT 0,
                end_day INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL(
            """CREATE TABLE loan (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, borrowed INTEGER NOT NULL,
                total_paise INTEGER NOT NULL, paid_paise INTEGER NOT NULL DEFAULT 0, due_day INTEGER NOT NULL DEFAULT 0
            )""",
        )
        createInvest(db)
        db.execSQL(
            """CREATE TABLE repeat_txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount_paise INTEGER NOT NULL, type TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '', account_id INTEGER NOT NULL DEFAULT 0, day_of_month INTEGER NOT NULL,
                start_day INTEGER NOT NULL, last_day INTEGER NOT NULL DEFAULT 0,
                rtype TEXT NOT NULL DEFAULT 'Other', pct INTEGER NOT NULL DEFAULT 0, paid_month INTEGER NOT NULL DEFAULT 0
            )""",
        )
    }

    fun createInvest(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS invest (
                id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, invested_paise INTEGER NOT NULL DEFAULT 0,
                value_paise INTEGER NOT NULL DEFAULT 0
            )""",
        )
    }

    private fun db(context: Context) = Db.get(context).database()

    // Investments

    fun investments(context: Context): List<Investment> =
        db(context).rawQuery("SELECT id, name, invested_paise, value_paise FROM invest ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Investment(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3))) }
        }

    fun saveInvestment(context: Context, id: Long?, name: String, invested: Long, value: Long) {
        val v = ContentValues().apply { put("name", name); put("invested_paise", invested); put("value_paise", value) }
        if (id == null) db(context).insert("invest", null, v) else db(context).update("invest", v, "id = ?", arrayOf(id.toString()))
    }

    fun deleteInvestment(context: Context, id: Long) {
        db(context).delete("invest", "id = ?", arrayOf(id.toString()))
    }

    // Goals

    fun goals(context: Context): List<Goal> =
        db(context).rawQuery("SELECT id, name, emoji, savings, target_paise, saved_paise, end_day FROM goal ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Goal(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1, c.getLong(4), c.getLong(5), c.getLong(6))) }
        }

    fun saveGoal(context: Context, id: Long?, name: String, emoji: String, savings: Boolean, target: Long, saved: Long, endDay: Long) {
        val v = ContentValues().apply {
            put("name", name); put("emoji", emoji); put("savings", if (savings) 1 else 0)
            put("target_paise", target); put("saved_paise", saved); put("end_day", endDay)
        }
        if (id == null) db(context).insert("goal", null, v) else db(context).update("goal", v, "id = ?", arrayOf(id.toString()))
    }

    fun addToGoal(context: Context, id: Long, paise: Long) {
        db(context).execSQL("UPDATE goal SET saved_paise = MAX(0, saved_paise + ?) WHERE id = ?", arrayOf(paise, id))
    }

    fun deleteGoal(context: Context, id: Long) {
        db(context).delete("goal", "id = ?", arrayOf(id.toString()))
    }

    // Loans

    fun loans(context: Context): List<Loan> =
        db(context).rawQuery("SELECT id, name, borrowed, total_paise, paid_paise, due_day FROM loan ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Loan(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getLong(3), c.getLong(4), c.getLong(5))) }
        }

    fun saveLoan(context: Context, id: Long?, name: String, borrowed: Boolean, total: Long, paid: Long, dueDay: Long) {
        val v = ContentValues().apply {
            put("name", name); put("borrowed", if (borrowed) 1 else 0)
            put("total_paise", total); put("paid_paise", paid); put("due_day", dueDay)
        }
        if (id == null) db(context).insert("loan", null, v) else db(context).update("loan", v, "id = ?", arrayOf(id.toString()))
    }

    fun addToLoan(context: Context, id: Long, paise: Long) {
        db(context).execSQL("UPDATE loan SET paid_paise = MIN(total_paise, MAX(0, paid_paise + ?)) WHERE id = ?", arrayOf(paise, id))
    }

    fun deleteLoan(context: Context, id: Long) {
        db(context).delete("loan", "id = ?", arrayOf(id.toString()))
    }

    // Repeating transactions

    fun repeats(context: Context): List<Repeat> =
        db(context).rawQuery(
            "SELECT id, title, amount_paise, type, category, account_id, day_of_month, start_day, last_day, rtype, pct, paid_month FROM repeat_txn ORDER BY day_of_month, id", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Repeat(c.getLong(0), c.getString(1), c.getLong(2), TxnType.valueOf(c.getString(3)), c.getString(4), c.getLong(5), c.getInt(6), c.getLong(7), c.getLong(8), c.getString(9), c.getInt(10), c.getInt(11)),
                )
            }
        }

    fun saveRepeat(context: Context, id: Long?, title: String, amount: Long, type: TxnType, category: String, accountId: Long, dayOfMonth: Int, rtype: String = "Other", pct: Int = 0) {
        val v = ContentValues().apply {
            put("title", title); put("amount_paise", amount); put("type", type.name); put("category", category)
            put("account_id", accountId); put("day_of_month", dayOfMonth); put("rtype", rtype); put("pct", pct)
        }
        if (id == null) {
            v.put("start_day", LocalDate.now().toEpochDay())
            db(context).insert("repeat_txn", null, v)
        } else {
            db(context).update("repeat_txn", v, "id = ?", arrayOf(id.toString()))
        }
    }

    fun deleteRepeat(context: Context, id: Long) {
        db(context).delete("repeat_txn", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Repeats no longer add spends of their own: the bank's debit message is the one record, and it is matched
     * to its repeat (see [matchIncoming]). Kept so older callers still work.
     */
    fun applyRepeats(context: Context): Int = 0

    private fun yearMonth(d: LocalDate) = d.year * 100 + d.monthValue

    /** What came in this month plus the salary-type repeats still expected. Percent repeats are based on this. */
    fun monthIncome(context: Context, today: LocalDate = LocalDate.now()): Pair<Long, Long> {
        val start = today.withDayOfMonth(1).toEpochDay()
        val received = db(context).rawQuery(
            "SELECT COALESCE(SUM(amount_paise), 0) FROM txn WHERE type = 'CREDIT' AND kind = 'NORMAL' AND epoch_day >= ? AND category NOT IN ('Transfer', 'Card bill payment', 'Cash withdrawal', 'Investments')",
            arrayOf(start.toString()),
        ).use { it.moveToFirst(); it.getLong(0) }
        val expected = repeats(context).filter { it.type == TxnType.CREDIT && it.pct == 0 && it.paidMonth != yearMonth(today) && occurrence(it, today) >= LocalDate.ofEpochDay(it.startDay) }
            .sumOf { it.amountPaise }
        return received to expected
    }

    private fun occurrence(r: Repeat, day: LocalDate) = app.expensetracker.core.RepeatPlan.occurrence(YearMonth.from(day), r.dayOfMonth)

    /** Every repeat's turn this month, with tithe-style amounts worked out from this month's income. */
    fun dues(context: Context, today: LocalDate = LocalDate.now()): List<RepeatDue> {
        val (received, expected) = monthIncome(context, today)
        return dues(repeats(context), today, received + expected)
    }

    fun dues(all: List<Repeat>, today: LocalDate, incomePaise: Long): List<RepeatDue> =
        all.mapNotNull { r ->
            val day = occurrence(r, today)
            if (day.toEpochDay() < r.startDay && r.startDay > 0 && day.isBefore(LocalDate.ofEpochDay(r.startDay))) null
            else RepeatDue(r, day, app.expensetracker.core.RepeatPlan.amount(r.amountPaise, r.pct, incomePaise), r.paidMonth == yearMonth(today))
        }.sortedBy { it.day }

    /** Repeats that still have to be paid in the next [days] days, overdue ones first. Looks into next month too. */
    fun upcoming(context: Context, today: LocalDate = LocalDate.now(), days: Int = 7): List<RepeatDue> {
        val (received, expected) = monthIncome(context, today)
        val all = repeats(context)
        val thisMonth = dues(all, today, received + expected).filter { !it.paid && it.day <= today.plusDays(days.toLong()) }
        val next = today.plusMonths(1).withDayOfMonth(1)
        val nextMonth = all.mapNotNull { r ->
            val day = occurrence(r, next)
            if (day <= today.plusDays(days.toLong()) && r.paidMonth != yearMonth(next)) RepeatDue(r, day, app.expensetracker.core.RepeatPlan.amount(r.amountPaise, r.pct, received + expected), false) else null
        }
        return (thisMonth + nextMonth).sortedBy { it.day }
    }

    fun markPaid(context: Context, id: Long, paid: Boolean) {
        db(context).execSQL("UPDATE repeat_txn SET paid_month = ? WHERE id = ?", arrayOf(if (paid) yearMonth(LocalDate.now()) else 0, id))
    }

    /**
     * Called for each new bank message. When it is a repeat arriving (same amount, near its date) the repeat is
     * marked paid for that month, the message takes the repeat's category and name, and any older entry the
     * repeat made for itself is dropped so the money is counted once.
     */
    fun matchIncoming(context: Context, txnId: Long): Boolean {
        val database = db(context)
        val t = Db.get(context).get(txnId) ?: return false
        if (t.source != "sms" || t.kind != TxnKind.NORMAL) return false
        val day = LocalDate.ofEpochDay(t.epochDay)
        val (received, expected) = monthIncome(context, day)
        val match = repeats(context).firstOrNull { r ->
            r.type == t.type && r.paidMonth != yearMonth(day) && app.expensetracker.core.RepeatPlan.matches(
                day, t.amountPaise, r.dayOfMonth, app.expensetracker.core.RepeatPlan.amount(r.amountPaise, r.pct, received + expected), r.pct,
            )
        } ?: return false
        database.execSQL("UPDATE repeat_txn SET paid_month = ? WHERE id = ?", arrayOf(yearMonth(day), match.id))
        if (t.category.isEmpty() && match.category.isNotEmpty()) database.execSQL("UPDATE txn SET category = ? WHERE id = ?", arrayOf(match.category, txnId))
        if (t.merchant.isNullOrBlank()) database.execSQL("UPDATE txn SET merchant = ? WHERE id = ?", arrayOf(match.title, txnId))
        database.execSQL("DELETE FROM txn WHERE source = 'repeat' AND dedup LIKE ? AND ABS(epoch_day - ?) <= 5", arrayOf("rep:${match.id}:%", t.epochDay))
        return true
    }

    /**
     * One-off tidy up of the time repeats made their own spends: each is merged with the bank debit that
     * matches it. Entries with no matching bank message stay, so nothing paid in cash disappears.
     * Returns how many were removed.
     */
    fun mergeOldRepeatEntries(context: Context): Int {
        val database = db(context)
        val all = repeats(context).associateBy { it.id }
        val allTxns = Db.get(context).all()
        val old = allTxns.filter { it.source == "repeat" }
        var removed = 0
        for (e in old) {
            val rid = Regex("""rep:(\d+):""").find(dedupOf(database, e.id) ?: "")?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val r = all[rid] ?: continue
            val day = LocalDate.ofEpochDay(e.epochDay)
            val bank = allTxns.firstOrNull {
                it.source == "sms" && it.type == e.type && it.kind == TxnKind.NORMAL &&
                    app.expensetracker.core.RepeatPlan.matches(LocalDate.ofEpochDay(it.epochDay), it.amountPaise, r.dayOfMonth, e.amountPaise, 0) &&
                    Math.abs(it.epochDay - e.epochDay) <= 5
            } ?: continue
            if (bank.category.isEmpty() && e.category.isNotEmpty()) database.execSQL("UPDATE txn SET category = ? WHERE id = ?", arrayOf(e.category, bank.id))
            if (bank.merchant.isNullOrBlank()) database.execSQL("UPDATE txn SET merchant = ? WHERE id = ?", arrayOf(e.merchant ?: r.title, bank.id))
            database.execSQL("DELETE FROM txn WHERE id = ?", arrayOf(e.id))
            if (yearMonth(day) == yearMonth(LocalDate.now()) && r.paidMonth != yearMonth(day)) markPaid(context, r.id, true)
            removed++
        }
        return removed
    }

    private fun dedupOf(database: SQLiteDatabase, id: Long): String? =
        database.rawQuery("SELECT dedup FROM txn WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }
}
