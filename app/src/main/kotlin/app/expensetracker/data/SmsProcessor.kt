package app.expensetracker.data

import android.content.Context
import android.net.Uri
import app.expensetracker.Notifier
import app.expensetracker.core.SmsParser
import java.time.Instant
import java.time.ZoneId

data class ImportResult(val scanned: Int, val added: Int)

object SmsProcessor {

    /** Parses one SMS and saves it. Returns the new transaction id, or -1 if it was ignored or already saved. */
    fun process(context: Context, body: String, sender: String?, receivedMillis: Long, notify: Boolean): Long {
        val parsed = SmsParser.parse(body, sender) ?: return -1
        val db = Db.get(context)
        val day = Instant.ofEpochMilli(receivedMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        // Same SMS seen live and again during import lands on the same key (body + minute).
        val id = db.insertParsed(parsed, day, "sms", body + "|" + receivedMillis / 60_000)
        if (id > 0 && notify) db.get(id)?.let { Notifier.show(context, it) }
        return id
    }

    /** Reads the whole inbox once. Needs the READ_SMS permission. */
    fun importInbox(context: Context): ImportResult {
        var scanned = 0
        var added = 0
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date ASC",
        ) ?: return ImportResult(0, 0)
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow("address")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                scanned++
                val body = c.getString(bodyIdx) ?: continue
                if (process(context, body, c.getString(addressIdx), c.getLong(dateIdx), notify = false) > 0) added++
            }
        }
        return ImportResult(scanned, added)
    }
}
