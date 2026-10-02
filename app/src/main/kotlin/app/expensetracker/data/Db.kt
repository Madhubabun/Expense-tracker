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
    /** The wallet this belongs to, or 0 for none. */
    val accountId: Long = 0,
    /** Comma separated tags, lowercase, for example "trip,work". */
    val tags: String = "",
    /** File name of an attached receipt photo, if any. */
    val receipt: String? = null,
    /** Currency it was spent in; INR unless you entered a foreign amount. */
    val currency: String = "INR",
    /** Amount in [currency] (in its smallest unit) when it is not INR, else 0. */
    val origPaise: Long = 0,
    /** The original bank SMS text, when it was saved (messages from before this feature have none). */
    val body: String? = null,
)

enum class AccountKind(val label: String, val emoji: String) {
    CASH("Cash", "💵"), BANK("Bank", "🏦"), CARD("Credit card", "💳"), WALLET("Wallet", "👛"),
}

/** A place money sits: cash, a bank account, a card or an e-wallet. [key] ties SMS to it (bank plus last digits). */
data class Account(
    val id: Long,
    val name: String,
    val kind: AccountKind,
    val openingPaise: Long,
    val color: Long,
    val key: String?,
)

/**
 * A category with its icon. [image] is a file name inside the app's private folder (a picture the
 * user picked); when it is null the [emoji] is shown instead. [color] is ARGB.
 */
data class Category(val name: String, val emoji: String, val color: Long, val image: String?, val builtin: Boolean)

/** All data lives in this one SQLite file on the phone. Nothing is sent anywhere. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "expenses.db", null, 10) {

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
                account_id INTEGER NOT NULL DEFAULT 0,
                tags TEXT NOT NULL DEFAULT '',
                receipt TEXT,
                currency TEXT NOT NULL DEFAULT 'INR',
                orig_paise INTEGER NOT NULL DEFAULT 0,
                pair_state INTEGER NOT NULL DEFAULT 0,
                body TEXT,
                keep_both INTEGER NOT NULL DEFAULT 0,
                dedup TEXT UNIQUE,
                created_at INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX idx_txn_day ON txn(epoch_day)")
        // Remembers the category you picked for a merchant, so the next payment there is tagged automatically.
        db.execSQL("CREATE TABLE merchant_category (merchant TEXT PRIMARY KEY, category TEXT NOT NULL)")
        createCategories(db)
        createCategoryBudgets(db)
        createAccounts(db)
        Plans.createTables(db)
        createWordCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE txn ADD COLUMN at_ms INTEGER NOT NULL DEFAULT 0")
            createCategories(db)
        }
        if (oldVersion < 3) createCategoryBudgets(db)
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE txn ADD COLUMN account_id INTEGER NOT NULL DEFAULT 0")
            createAccounts(db)
            // Give every spend read from an SMS its wallet, and put hand-added ones in Cash.
            val cash = cashAccountId(db)
            db.execSQL("UPDATE txn SET account_id = $cash WHERE source = 'manual'")
            val rows = db.rawQuery("SELECT DISTINCT bank, account FROM txn WHERE source != 'manual'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
            }
            rows.forEach { (bank, acct) ->
                val id = ensureSmsAccount(db, bank, acct)
                db.execSQL(
                    "UPDATE txn SET account_id = ? WHERE source != 'manual' AND bank IS ? AND account IS ?",
                    arrayOf<Any?>(id, bank, acct),
                )
            }
        }
        if (oldVersion < 5) upgradeToV5(db)
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE txn ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
            db.execSQL("ALTER TABLE txn ADD COLUMN orig_paise INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 7) createWordCategories(db)
        if (oldVersion < 8) db.execSQL("ALTER TABLE txn ADD COLUMN pair_state INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 10) {
            db.execSQL("INSERT OR IGNORE INTO category(name, emoji, color, image, builtin, sort) VALUES (?, ?, ?, NULL, 1, 99)", arrayOf(Categories.CASH, "🏧", 0xFF5CE08A))
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE txn ADD COLUMN body TEXT")
            db.execSQL("ALTER TABLE txn ADD COLUMN keep_both INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Words from shop names and your notes, and how often each went with a category. Lets the app guess next time. */
    private fun createWordCategories(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE word_category (word TEXT NOT NULL, category TEXT NOT NULL, n INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (word, category))")
    }

    private val stopWords = setOf("the", "and", "for", "to", "from", "upi", "pay", "paid", "payment", "bank", "sent", "via", "ltd", "pvt", "india", "private", "limited", "with", "online")

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in stopWords && !it.all { c -> c.isDigit() } }.distinct()

    @Synchronized
    private fun learnWords(text: String, category: String) {
        if (category.isEmpty()) return
        words(text).forEach { w ->
            writableDatabase.execSQL("INSERT OR IGNORE INTO word_category(word, category, n) VALUES (?, ?, 0)", arrayOf(w, category))
            writableDatabase.execSQL("UPDATE word_category SET n = n + 1 WHERE word = ? AND category = ?", arrayOf(w, category))
        }
    }

    /** The category that most often went with the words in [text], or null when nothing was learned yet. */
    fun suggestFromWords(text: String): String? {
        val ws = words(text)
        if (ws.isEmpty()) return null
        val votes = HashMap<String, Int>()
        ws.forEach { w ->
            readableDatabase.rawQuery("SELECT category, n FROM word_category WHERE word = ?", arrayOf(w)).use { c ->
                while (c.moveToNext()) votes.merge(c.getString(0), c.getInt(1), Int::plus)
            }
        }
        val best = votes.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= 2 }
    }

    /** Earlier spend amounts at the same shop (or in the same category when there is no shop), newest first. */
    fun recentSpendAmounts(merchant: String?, category: String, excludeId: Long, limit: Int = 20): List<Long> {
        val (where, args) = when {
            !merchant.isNullOrBlank() -> "lower(merchant) = ?" to arrayOf(merchant.lowercase())
            category.isNotEmpty() -> "category = ?" to arrayOf(category)
            else -> return emptyList()
        }
        return readableDatabase.rawQuery(
            "SELECT amount_paise FROM txn WHERE type = 'DEBIT' AND kind = 'NORMAL' AND id != $excludeId AND $where ORDER BY epoch_day DESC, id DESC LIMIT $limit", args,
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    private fun upgradeToV5(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE txn ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE txn ADD COLUMN receipt TEXT")
        Plans.createTables(db)
    }

    private fun createAccounts(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE account (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                opening_paise INTEGER NOT NULL DEFAULT 0,
                color INTEGER NOT NULL,
                acct_key TEXT UNIQUE,
                sort INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL(
            "INSERT INTO account(name, kind, opening_paise, color, acct_key, sort) VALUES ('Cash', 'CASH', 0, ${0xFF2EF2E0}, 'cash', 0)",
        )
    }

    private fun cashAccountId(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT id FROM account WHERE acct_key = 'cash'", null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

    /** Finds or makes the wallet for a bank and its last digits. Returns 0 when the SMS named neither. */
    private fun ensureSmsAccount(db: SQLiteDatabase, bank: String?, account: String?): Long {
        if (bank.isNullOrBlank() && account.isNullOrBlank()) return 0L
        val key = (bank.orEmpty() + "|" + account.orEmpty()).lowercase()
        db.rawQuery("SELECT id FROM account WHERE acct_key = ?", arrayOf(key)).use { if (it.moveToFirst()) return it.getLong(0) }
        val name = listOfNotNull(bank?.takeIf { it.isNotBlank() }, account?.takeIf { it.isNotBlank() }?.let { "••$it" }).joinToString(" ")
        val colors = listOf(0xFF4DA3FF, 0xFFB18CFF, 0xFFFF4FD8, 0xFFFFB938, 0xFFB6FF5C, 0xFFFF8A4C)
        val count = db.rawQuery("SELECT COUNT(*) FROM account", null).use { it.moveToFirst(); it.getInt(0) }
        val v = ContentValues().apply {
            put("name", name); put("kind", AccountKind.BANK.name); put("opening_paise", 0)
            put("color", colors[count % colors.size]); put("acct_key", key); put("sort", count)
        }
        return db.insert("account", null, v)
    }

    fun accounts(): List<Account> =
        readableDatabase.query("account", null, null, null, null, null, "sort ASC, id ASC").use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Account(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        kind = AccountKind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
                        openingPaise = c.getLong(c.getColumnIndexOrThrow("opening_paise")),
                        color = c.getLong(c.getColumnIndexOrThrow("color")),
                        key = c.getString(c.getColumnIndexOrThrow("acct_key")),
                    ),
                )
            }
        }

    fun cashAccountId(): Long = cashAccountId(readableDatabase)

    /** After restoring an older backup that had no wallets, make sure Cash exists again. */
    fun ensureCash() {
        if (cashAccountId() == 0L) {
            writableDatabase.execSQL(
                "INSERT INTO account(name, kind, opening_paise, color, acct_key, sort) VALUES ('Cash', 'CASH', 0, ${0xFF2EF2E0}, 'cash', 0)",
            )
        }
    }

    @Synchronized
    fun addAccount(name: String, kind: AccountKind, openingPaise: Long, color: Long): Long {
        val next = readableDatabase.rawQuery("SELECT COALESCE(MAX(sort), 0) + 1 FROM account", null).use { it.moveToFirst(); it.getInt(0) }
        val v = ContentValues().apply {
            put("name", name); put("kind", kind.name); put("opening_paise", openingPaise); put("color", color); put("sort", next)
        }
        return writableDatabase.insert("account", null, v)
    }

    fun updateAccount(id: Long, name: String, kind: AccountKind, openingPaise: Long, color: Long) {
        val v = ContentValues().apply {
            put("name", name); put("kind", kind.name); put("opening_paise", openingPaise); put("color", color)
        }
        writableDatabase.update("account", v, "id = ?", arrayOf(id.toString()))
    }

    /** Spends in a deleted wallet stay, with no wallet. Cash can't be deleted. */
    @Synchronized
    fun deleteAccount(id: Long) {
        if (id == cashAccountId()) return
        writableDatabase.execSQL("UPDATE txn SET account_id = 0 WHERE account_id = ?", arrayOf(id))
        writableDatabase.delete("account", "id = ?", arrayOf(id.toString()))
    }

    /**
     * Carves [paise] out of a spend into a new spend in [category] (same day, wallet, tags and note).
     * Returns false if the amount is not between zero and the spend's amount.
     */
    @Synchronized
    fun splitOff(id: Long, paise: Long, category: String): Boolean {
        val t = get(id) ?: return false
        if (paise <= 0 || paise >= t.amountPaise) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE txn SET amount_paise = amount_paise - ?, currency = 'INR', orig_paise = 0 WHERE id = ?", arrayOf(paise, id))
            val v = ContentValues().apply {
                put("amount_paise", paise); put("type", t.type.name); put("kind", t.kind.name)
                put("merchant", t.merchant); put("bank", t.bank); put("account", t.account)
                put("epoch_day", t.epochDay); put("at_ms", t.atMillis)
                put("category", category); put("comment", t.comment); put("source", "split")
                put("account_id", t.accountId); put("tags", t.tags); put("created_at", System.currentTimeMillis())
            }
            db.insert("txn", null, v)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    /**
     * Cleans up confirmations that were counted before pairing existed. Each item is a parsed confirmation SMS and
     * the time it arrived. The spend that came from it is removed when a separate alert already covers the same debit.
     * Returns how many spends were removed.
     */
    @Synchronized
    fun dedupeConfirmations(items: List<Pair<ParsedTxn, Long>>): Int {
        val db = writableDatabase
        val confirmTimes = items.map { it.second }.toSet()
        var removed = 0
        for ((p, at) in items) {
            val account = ensureSmsAccount(db, p.bank, p.account)
            if (account == 0L) continue
            val row = db.rawQuery(
                "SELECT id, epoch_day, category FROM txn WHERE at_ms = ? AND amount_paise = ? AND account_id = ? AND source = 'sms' AND pair_state != 2 LIMIT 1",
                arrayOf(at.toString(), p.amountPaise.toString(), account.toString()),
            ).use { if (it.moveToFirst()) Triple(it.getLong(0), it.getLong(1), it.getString(2)) else null } ?: continue
            val alert = db.rawQuery(
                "SELECT id, at_ms FROM txn WHERE id != ? AND amount_paise = ? AND type = ? AND account_id = ? AND source = 'sms' AND pair_state = 0 AND ABS(epoch_day - ?) <= 1 ORDER BY at_ms",
                arrayOf(row.first.toString(), p.amountPaise.toString(), p.type.name, account.toString(), row.second.toString()),
            ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) } }
                .firstOrNull { it.second !in confirmTimes }?.first ?: continue
            if (row.third.isNotEmpty()) db.execSQL("UPDATE txn SET category = ? WHERE id = ? AND category = ''", arrayOf(row.third, alert))
            db.execSQL("UPDATE txn SET pair_state = 2 WHERE id = ?", arrayOf(alert))
            db.delete("txn", "id = ?", arrayOf(row.first.toString()))
            removed++
        }
        return removed
    }

    fun setTags(txnId: Long, tags: String) {
        writableDatabase.execSQL("UPDATE txn SET tags = ? WHERE id = ?", arrayOf(tags, txnId))
    }

    fun setReceipt(txnId: Long, file: String?) {
        writableDatabase.execSQL("UPDATE txn SET receipt = ? WHERE id = ?", arrayOf<Any?>(file, txnId))
    }

    fun setTxnAccount(txnId: Long, accountId: Long) {
        writableDatabase.execSQL("UPDATE txn SET account_id = ? WHERE id = ?", arrayOf(accountId, txnId))
    }

    private fun createCategoryBudgets(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE category_budget (category TEXT PRIMARY KEY, paise INTEGER NOT NULL)")
    }

    /** Monthly limit per category, in paise. */
    fun categoryBudgets(): Map<String, Long> =
        readableDatabase.rawQuery("SELECT category, paise FROM category_budget", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
        }

    /** A limit of 0 or less removes the budget. */
    fun setCategoryBudget(category: String, paise: Long) {
        if (paise <= 0) {
            writableDatabase.delete("category_budget", "category = ?", arrayOf(category))
        } else {
            writableDatabase.execSQL("INSERT OR REPLACE INTO category_budget(category, paise) VALUES (?, ?)", arrayOf(category, paise))
        }
    }

    /** Raw access for backup and restore. */
    internal fun database(): SQLiteDatabase = writableDatabase

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
    fun insertParsed(p: ParsedTxn, fallbackEpochDay: Long, atMillis: Long, source: String, dedupSeed: String, body: String? = null): Long {
        // A transfer between your own accounts has one reference on both the debit and the credit, so credits get their own key.
        val dedup = p.ref?.let { if (p.type == TxnType.CREDIT) "refc:$it" else "ref:$it" } ?: ("h:" + sha256(dedupSeed))
        // Banks often send an alert and later a "processed" confirmation for the same debit. Pair them so
        // the money is counted once. pair_state: 0 alert waiting, 1 confirmation waiting, 2 paired.
        val accountId = ensureSmsAccount(writableDatabase, p.bank, p.account)
        val day = p.epochDay ?: fallbackEpochDay
        if (accountId != 0L && p.type == TxnType.DEBIT) {
            val wanted = if (p.confirmation) 0 else 1
            val match = writableDatabase.rawQuery(
                "SELECT id FROM txn WHERE account_id = ? AND amount_paise = ? AND type = 'DEBIT' AND source = 'sms' AND pair_state = ? AND ABS(epoch_day - ?) <= 1 ORDER BY at_ms LIMIT 1",
                arrayOf(accountId.toString(), p.amountPaise.toString(), wanted.toString(), day.toString()),
            ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
            if (match > 0) {
                val v = ContentValues().apply { put("pair_state", 2) }
                if (!p.confirmation) {
                    // The alert is the fuller message: fill in what the confirmation row lacked.
                    p.merchant?.let { m -> writableDatabase.execSQL("UPDATE txn SET merchant = ? WHERE id = ? AND (merchant IS NULL OR merchant = '')", arrayOf(m, match)) }
                    p.ref?.let { r -> writableDatabase.execSQL("UPDATE txn SET ref = ? WHERE id = ? AND ref IS NULL", arrayOf(r, match)) }
                }
                writableDatabase.update("txn", v, "id = ?", arrayOf(match.toString()))
                if (!p.confirmation && body != null) writableDatabase.execSQL("UPDATE txn SET body = ? WHERE id = ? AND body IS NULL", arrayOf(body, match))
                return -1
            }
        }
        val learned = p.merchant?.let { learnedCategory(it) }
        val category = learned ?: p.suggestedCategory?.takeIf { it.isNotEmpty() } ?: p.merchant?.let { suggestFromWords(it) }.orEmpty()
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
            put("account_id", accountId)
            put("pair_state", if (p.confirmation) 1 else 0)
            put("body", body)
            put("dedup", dedup)
            put("created_at", System.currentTimeMillis())
        }
        val id = writableDatabase.insertWithOnConflict("txn", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        // Seen before this update stored messages: keep the original text for the row that already exists.
        if (id < 0 && body != null) writableDatabase.execSQL("UPDATE txn SET body = ? WHERE dedup = ? AND body IS NULL", arrayOf(body, dedup))
        return id
    }

    /** True when a saved spend already came from exactly this SMS text. */
    fun hasBody(body: String, atMillis: Long): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM txn WHERE body = ? AND ABS(at_ms - ?) < 120000 LIMIT 1", arrayOf(body, atMillis.toString())).use { it.moveToFirst() }

    /**
     * Bank debits that look like the same payment: same wallet, same amount, within ten minutes, and not
     * yet marked "keep both". Maps each such spend to its partner.
     */
    fun possibleRepeats(): Map<Long, Long> =
        readableDatabase.rawQuery(
            """SELECT a.id, b.id FROM txn a JOIN txn b ON a.id != b.id
               WHERE a.type = 'DEBIT' AND b.type = 'DEBIT' AND a.source = 'sms' AND b.source = 'sms'
               AND a.account_id != 0 AND a.account_id = b.account_id AND a.amount_paise = b.amount_paise
               AND a.keep_both = 0 AND b.keep_both = 0 AND ABS(a.at_ms - b.at_ms) <= 600000""",
            null,
        ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) } }

    /** Counts the two as one: [drop] is removed, and [keep] takes its category if it has none. */
    @Synchronized
    fun mergeRepeat(keep: Long, drop: Long) {
        val d = get(drop) ?: return
        val k = get(keep) ?: return
        if (k.category.isEmpty() && d.category.isNotEmpty()) writableDatabase.execSQL("UPDATE txn SET category = ? WHERE id = ?", arrayOf(d.category, keep))
        if (k.merchant.isNullOrBlank() && !d.merchant.isNullOrBlank()) writableDatabase.execSQL("UPDATE txn SET merchant = ? WHERE id = ?", arrayOf(d.merchant, keep))
        writableDatabase.delete("txn", "id = ?", arrayOf(drop.toString()))
    }

    /** Both are real payments: stop flagging them. */
    fun keepBoth(a: Long, b: Long) {
        writableDatabase.execSQL("UPDATE txn SET keep_both = 1 WHERE id IN (?, ?)", arrayOf(a, b))
    }

    /** Recent bank messages that went wrong or look odd, with long numbers masked, ready to share. */
    fun problemReport(): String {
        val repeats = possibleRepeats()
        val rows = all().filter { it.source == "sms" && (it.id in repeats || it.merchant.isNullOrBlank()) }.take(15)
        if (rows.isEmpty()) return "No problem messages found."
        return rows.joinToString("\n\n") { t ->
            val why = if (t.id in repeats) "possible repeat" else "no shop name"
            "[$why] ${if (t.type == TxnType.DEBIT) "debit" else "credit"} ${t.amountPaise / 100.0}, shop=${t.merchant ?: "-"}, ref=${t.ref?.let { mask(it) } ?: "-"}\n" +
                (t.body?.let { mask(it) } ?: "(original message not saved)")
        }
    }

    private fun mask(s: String) = s.replace(Regex("\\d{6,}")) { "•".repeat(it.value.length - 2) + it.value.takeLast(2) }

    fun insertManual(
        amountPaise: Long,
        type: TxnType,
        epochDay: Long,
        category: String,
        comment: String,
        merchant: String?,
        accountId: Long = cashAccountId(),
        tags: String = "",
        currency: String = "INR",
        origPaise: Long = 0,
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
            put("account_id", accountId)
            put("tags", tags)
            put("currency", currency)
            put("orig_paise", origPaise)
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
        learnWords((merchant ?: "") + " " + comment, category)
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
        accountId = getLong(getColumnIndexOrThrow("account_id")),
        tags = getString(getColumnIndexOrThrow("tags")),
        receipt = getString(getColumnIndexOrThrow("receipt")),
        currency = getString(getColumnIndexOrThrow("currency")),
        origPaise = getLong(getColumnIndexOrThrow("orig_paise")),
        body = getString(getColumnIndexOrThrow("body")),
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
