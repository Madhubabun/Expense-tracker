package app.expensetracker.data

import android.content.Context
import android.net.Uri
import app.expensetracker.Alerts
import app.expensetracker.Notifier
import app.expensetracker.TodayWidget
import app.expensetracker.core.ParsedTxn
import app.expensetracker.core.SmsParser
import java.time.Instant
import java.time.ZoneId

data class ImportResult(val scanned: Int, val added: Int, val skippedOld: Int)

object SmsProcessor {

    /** Returned by [process] for a transaction dated before the tracking start day. */
    const val SKIPPED_OLD = -2L

    /**
     * Parses one SMS and saves it. Returns the new transaction id, -1 if it was ignored or already saved,
     * or [SKIPPED_OLD] if it is dated before the day tracking started.
     */
    fun process(context: Context, body: String, sender: String?, receivedMillis: Long, notify: Boolean): Long {
        val parsed = SmsParser.parse(body, sender) ?: return -1
        val db = Db.get(context)
        val day = Instant.ofEpochMilli(receivedMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        // Same SMS seen live and again during import lands on the same key (body + minute).
        if ((parsed.epochDay ?: day) < Prefs.startDay(context)) return SKIPPED_OLD
        // A message that carries its own time (07:50:56) is the same transaction however often it arrives.
        val seed = if (Regex("""\d{1,2}:\d{2}:\d{2}""").containsMatchIn(body)) body else body + "|" + receivedMillis / 60_000
        val id = db.insertParsed(parsed, day, receivedMillis, "sms", seed, body)
        if (id > 0 && notify) {
            db.get(id)?.let { Notifier.show(context, it) }
            runCatching { Alerts.checkBudgets(context) }
            runCatching { Alerts.checkUnusual(context, id) }
            runCatching { TodayWidget.refresh(context) }
        }
        return id
    }

    /** Finds "processed" confirmations in the inbox and removes the extra spends they created. Returns how many. */
    fun cleanDuplicates(context: Context): Int {
        val items = mutableListOf<Pair<ParsedTxn, Long>>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), null, null, "date ASC",
        ) ?: return 0
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow("address")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val parsed = SmsParser.parse(c.getString(bodyIdx) ?: continue, c.getString(addressIdx)) ?: continue
                if (parsed.confirmation) items += parsed to c.getLong(dateIdx)
            }
        }
        return Db.get(context).dedupeConfirmations(items)
    }

    /** Reads the whole inbox once. Needs the READ_SMS permission. */
    fun importInbox(context: Context): ImportResult {
        var scanned = 0
        var added = 0
        var skippedOld = 0
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null,
            null,
            "date ASC",
        ) ?: return ImportResult(0, 0, 0)
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow("address")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                scanned++
                val body = c.getString(bodyIdx) ?: continue
                val r = process(context, body, c.getString(addressIdx), c.getLong(dateIdx), notify = false)
                if (r > 0) added++ else if (r == SKIPPED_OLD) skippedOld++
            }
        }
        return ImportResult(scanned, added, skippedOld)
    }
}
