package app.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.expensetracker.core.Categories
import app.expensetracker.core.ParsedTxn
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import java.security.MessageDigest

data class Txn(
    val id: Long,
    val amountPaise: Long,
    val type: TxnType,
    val kind: TxnKind,
    val merchant: String?,
    val bank: String?,
    val account: String?,
    val ref: String?,
    val epochDay: Long,
    /** When the SMS arrived (or the spend was added), in milliseconds. 0 if unknown. */
    val atMillis: Long,
    val category: String,
    val comment: String,
    val source: String,
)

/**
 * A category with its icon. [image] is a file name inside the app's private folder (a picture the
 * user picked); when it is null the [emoji] is shown instead. [color] is ARGB.
 */
data class Category(val name: String, val emoji: String, val color: Long, val image: String?, val builtin: Boolean)

/** All data lives in this one SQLite file on the phone. Nothing is sent anywhere. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "expenses.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_paise INTEGER NOT NULL,
                type TEXT NOT NULL,
                kind TEXT NOT NULL,
                merchant TEXT,
                bank TEXT,
                account TEXT,
                ref TEXT,
                epoch_day INTEGER NOT NULL,
                at_ms INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT '',
                comment TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL,
                dedup TEXT UNIQUE,
                created_at INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX idx_txn_day ON txn(epoch_day)")
        // Remembers the category you picked for a merchant, so the next payment there is tagged automatically.
        db.execSQL("CREATE TABLE merchant_category (merchant TEXT PRIMARY KEY, category TEXT NOT NULL)")
        createCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE txn ADD COLUMN at_ms INTEGER NOT NULL DEFAULT 0")
            createCategories(db)
        }
    }

    private fun createCategories(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE category (
                name TEXT PRIMARY KEY,
                emoji TEXT NOT NULL,
                color INTEGER NOT NULL,
                image TEXT,
                builtin INTEGER NOT NULL DEFAULT 0,
                sort INTEGER NOT NULL
            )""",
        )
        Categories.builtins.forEachIndexed { i, b ->
            val values = ContentValues().apply {
                put("name", b.name); put("emoji", b.emoji); put("color", b.argb); put("builtin", 1); put("sort", i)
            }
            db.insert("category", null, values)
        }
    }

    fun categories(): List<Category> =
        readableDatabase.query("category", null, null, null, null, null, "sort ASC, name ASC").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Category(
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            emoji = c.getString(c.getColumnIndexOrThrow("emoji")),
                            color = c.getLong(c.getColumnIndexOrThrow("color")),
                            image = c.getString(c.getColumnIndexOrThrow("image")),
                            builtin = c.getInt(c.getColumnIndexOrThrow("builtin")) == 1,
                        ),
                    )
                }
            }
        }

    /** Returns false if a category with that name already exists. */
    @Synchronized
    fun addCategory(name: String, emoji: String, color: Long, image: String?): Boolean {
        val exists = readableDatabase.rawQuery("SELECT 1 FROM category WHERE lower(name) = ?", arrayOf(name.lowercase()))
            .use { it.moveToFirst() }
        if (exists) return false
        val next = readableDatabase.rawQuery("SELECT COALESCE(MAX(sort), 0) + 1 FROM category", null).use { it.moveToFirst(); it.getInt(0) }
        val values = ContentValues().apply {
            put("name", name); put("emoji", emoji); put("color", color); put("image", image); put("builtin", 0); put("sort", next)
        }
        return writableDatabase.insert("category", null, values) != -1L
    }

    /** Removes a category you made. Spends that used it go back to "needs a category". */
    @Synchronized
    fun deleteCategory(name: String) {
        writableDatabase.delete("category", "name = ? AND builtin = 0", arrayOf(name))
        writableDatabase.execSQL("UPDATE txn SET category = '' WHERE category = ?", arrayOf(name))
        writableDatabase.execSQL("DELETE FROM merchant_category WHERE category = ?", arrayOf(name))
    }

    /** Saves a parsed SMS. Returns the new row id, or -1 if the same transaction was already saved. */
    @Synchronized
    fun insertParsed(p: ParsedTxn, fallbackEpochDay: Long, atMillis: Long, source: String, dedupSeed: String): Long {
        val dedup = p.ref?.let { "ref:$it" } ?: ("h:" + sha256(dedupSeed))
        val learned = p.merchant?.let { learnedCategory(it) }
        val category = learned ?: p.suggestedCategory.orEmpty()
        val values = ContentValues().apply {
            put("amount_paise", p.amountPaise)
            put("type", p.type.name)
            put("kind", p.kind.name)
            put("merchant", p.merchant)
            put("bank", p.bank)
            put("account", p.account)
            put("ref", p.ref)
            put("epoch_day", p.epochDay ?: fallbackEpochDay)
            put("at_ms", atMillis)
            put("category", category)
            put("comment", "")
            put("source", source)
            put("dedup", dedup)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("txn", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun insertManual(
        amountPaise: Long,
        type: TxnType,
        epochDay: Long,
        category: String,
        comment: String,
        merchant: String?,
    ): Long {
        val values = ContentValues().apply {
            put("amount_paise", amountPaise)
            put("type", type.name)
            put("kind", TxnKind.NORMAL.name)
            put("merchant", merchant)
            put("epoch_day", epochDay)
            // Today's spends get the current time; older days get midday so they sort sensibly.
            put("at_ms", if (epochDay == java.time.LocalDate.now().toEpochDay()) System.currentTimeMillis() else epochDay * 86_400_000L + 43_200_000L)
            put("category", category)
            put("comment", comment)
            put("source", "manual")
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("txn", null, values)
    }

    fun get(id: Long): Txn? =
        readableDatabase.query("txn", null, "id = ?", arrayOf(id.toString()), null, null, null).use {
            if (it.moveToFirst()) it.toTxn() else null
        }

    fun all(): List<Txn> =
        readableDatabase.query("txn", null, null, null, null, null, "epoch_day DESC, at_ms DESC, id DESC").use { c ->
            buildList { while (c.moveToNext()) add(c.toTxn()) }
        }

    fun categoriesInUse(): List<String> =
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*) c FROM txn WHERE category != '' GROUP BY category ORDER BY c DESC", null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    /**
     * Saves the category and comment for one transaction. The category is remembered for the merchant,
     * and every other transaction from that merchant that still has no category gets it too.
     */
    @Synchronized
    fun setCategoryAndComment(id: Long, category: String, comment: String) {
        val t = get(id) ?: return
        val values = ContentValues().apply {
            put("category", category)
            put("comment", comment)
        }
        writableDatabase.update("txn", values, "id = ?", arrayOf(id.toString()))
        val merchant = t.merchant
        if (category.isNotEmpty() && !merchant.isNullOrBlank()) {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO merchant_category(merchant, category) VALUES (?, ?)",
                arrayOf(merchant.lowercase(), category),
            )
            writableDatabase.execSQL(
                "UPDATE txn SET category = ? WHERE lower(merchant) = ? AND category = ''",
                arrayOf(category, merchant.lowercase()),
            )
        }
    }

    fun setComment(id: Long, comment: String) {
        val values = ContentValues().apply { put("comment", comment) }
        writableDatabase.update("txn", values, "id = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("txn", "id = ?", arrayOf(id.toString()))
    }

    private fun learnedCategory(merchant: String): String? =
        readableDatabase.rawQuery(
            "SELECT category FROM merchant_category WHERE merchant = ?", arrayOf(merchant.lowercase()),
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun Cursor.toTxn() = Txn(
        id = getLong(getColumnIndexOrThrow("id")),
        amountPaise = getLong(getColumnIndexOrThrow("amount_paise")),
        type = TxnType.valueOf(getString(getColumnIndexOrThrow("type"))),
        kind = TxnKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
        merchant = getString(getColumnIndexOrThrow("merchant")),
        bank = getString(getColumnIndexOrThrow("bank")),
        account = getString(getColumnIndexOrThrow("account")),
        ref = getString(getColumnIndexOrThrow("ref")),
        epochDay = getLong(getColumnIndexOrThrow("epoch_day")),
        atMillis = getLong(getColumnIndexOrThrow("at_ms")),
        category = getString(getColumnIndexOrThrow("category")),
        comment = getString(getColumnIndexOrThrow("comment")),
        source = getString(getColumnIndexOrThrow("source")),
    )

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }
    }
}
