package app.expensetracker.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the app knows, as one JSON file: spends, categories (with their pictures), budgets and
 * settings. Restoring replaces what is on the phone, which is what you want after a reinstall or a new phone.
 */
object Backup {
    private const val VERSION = 1
    private val TABLES = listOf("txn", "category", "merchant_category", "category_budget")

    fun export(context: Context): String {
        val db = Db.get(context).database()
        val root = JSONObject().put("version", VERSION).put("exportedAt", System.currentTimeMillis())
        root.put("budgetPaise", Prefs.budgetPaise(context)).put("startDay", Prefs.startDay(context))
        TABLES.forEach { table ->
            val rows = dump(db, table)
            if (table == "category") {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val image = row.optString("image", "")
                    if (image.isNotEmpty()) {
                        val f = File(File(context.filesDir, "category-images"), image)
                        if (f.exists()) row.put("image_data", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                    }
                }
            }
            root.put(table, rows)
        }
        return root.toString()
    }

    /** Returns the number of spends restored, or an error message. */
    fun restore(context: Context, json: String): Result<Int> = runCatching {
        val root = JSONObject(json)
        require(root.optInt("version", 0) in 1..VERSION) { "This is not a backup from this app." }
        val db = Db.get(context).database()
        db.beginTransaction()
        try {
            TABLES.forEach { db.delete(it, null, null) }
            TABLES.forEach { table -> load(db, table, root.optJSONArray(table) ?: JSONArray()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val images = File(context.filesDir, "category-images").apply { mkdirs() }
        val cats = root.optJSONArray("category") ?: JSONArray()
        for (i in 0 until cats.length()) {
            val row = cats.getJSONObject(i)
            val name = row.optString("image", "")
            val data = row.optString("image_data", "")
            if (name.isNotEmpty() && data.isNotEmpty()) File(images, name).writeBytes(Base64.decode(data, Base64.NO_WRAP))
        }
        Prefs.setBudgetPaise(context, root.optLong("budgetPaise", Prefs.DEFAULT_BUDGET_PAISE))
        Prefs.setStartDay(context, root.optLong("startDay", 0L))
        root.optJSONArray("txn")?.length() ?: 0
    }

    private fun dump(db: SQLiteDatabase, table: String): JSONArray {
        val out = JSONArray()
        db.query(table, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until c.columnCount) row.put(c.getColumnName(i), value(c, i))
                out.put(row)
            }
        }
        return out
    }

    private fun value(c: Cursor, i: Int): Any = when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        else -> JSONObject.NULL
    }

    private fun load(db: SQLiteDatabase, table: String, rows: JSONArray) {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val values = ContentValues()
            row.keys().forEach { key ->
                if (key == "image_data" || (table == "txn" && key == "id")) return@forEach
                when (val v = row.get(key)) {
                    JSONObject.NULL -> values.putNull(key)
                    is Number -> values.put(key, v.toLong())
                    else -> values.put(key, v.toString())
                }
            }
            db.insertOrThrow(table, null, values)
        }
    }
}
