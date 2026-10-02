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
)

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
        db.execSQL(
            """CREATE TABLE repeat_txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount_paise INTEGER NOT NULL, type TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '', account_id INTEGER NOT NULL DEFAULT 0, day_of_month INTEGER NOT NULL,
                start_day INTEGER NOT NULL, last_day INTEGER NOT NULL DEFAULT 0
            )""",
        )
    }

    private fun db(context: Context) = Db.get(context).database()

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
            "SELECT id, title, amount_paise, type, category, account_id, day_of_month, start_day, last_day FROM repeat_txn ORDER BY day_of_month, id", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Repeat(c.getLong(0), c.getString(1), c.getLong(2), TxnType.valueOf(c.getString(3)), c.getString(4), c.getLong(5), c.getInt(6), c.getLong(7), c.getLong(8)),
                )
            }
        }

    fun saveRepeat(context: Context, id: Long?, title: String, amount: Long, type: TxnType, category: String, accountId: Long, dayOfMonth: Int) {
        val v = ContentValues().apply {
            put("title", title); put("amount_paise", amount); put("type", type.name); put("category", category)
            put("account_id", accountId); put("day_of_month", dayOfMonth)
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
     * Adds a transaction for every repeat whose day has come since it last ran. A repeat made today
     * first fires on its next date, never backdated. Safe to call as often as you like.
     */
    fun applyRepeats(context: Context): Int {
        val database = db(context)
        val today = LocalDate.now()
        var added = 0
        repeats(context).forEach { r ->
            var month = YearMonth.from(LocalDate.ofEpochDay(r.startDay))
            var last = r.lastDay
            while (!month.isAfter(YearMonth.from(today))) {
                val occ = month.atDay(minOf(r.dayOfMonth, month.lengthOfMonth()))
                val day = occ.toEpochDay()
                if (day >= r.startDay && day > r.lastDay && !occ.isAfter(today)) {
                    val v = ContentValues().apply {
                        put("amount_paise", r.amountPaise); put("type", r.type.name); put("kind", TxnKind.NORMAL.name)
                        put("merchant", r.title); put("epoch_day", day)
                        put("at_ms", day * 86_400_000L + 43_200_000L)
                        put("category", r.category); put("comment", "Repeats every month")
                        put("source", "repeat"); put("account_id", r.accountId)
                        put("dedup", "rep:${r.id}:$day"); put("created_at", System.currentTimeMillis())
                    }
                    if (database.insertWithOnConflict("txn", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
                    if (day > last) last = day
                }
                month = month.plusMonths(1)
            }
            if (last != r.lastDay) database.execSQL("UPDATE repeat_txn SET last_day = ? WHERE id = ?", arrayOf(last, r.id))
        }
        return added
    }
}
