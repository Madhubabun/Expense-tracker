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
        if (db.hasBody(body, receivedMillis)) return -1 // this exact message was already saved at about the same time
        val day = Instant.ofEpochMilli(receivedMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        // Same SMS seen live and again during import lands on the same key (body + minute).
        if ((parsed.epochDay ?: day) < Prefs.startDay(context)) return SKIPPED_OLD
        // A message that carries its own time (07:50:56) is the same transaction however often it arrives.
        val seed = if (Regex("""\d{1,2}:\d{2}:\d{2}""").containsMatchIn(body)) body else body + "|" + receivedMillis / 60_000
        val id = db.insertParsed(parsed, day, receivedMillis, "sms", seed, body)
        if (id > 0 && notify) {
            db.get(id)?.let { runCatching { Notifier.show(context, it) } }
            runCatching { Alerts.checkBudgets(context) }
            runCatching { Alerts.checkUnusual(context, id) }
            runCatching { TodayWidget.refresh(context) }
        }
        return id
    }

    /**
     * Looks for bank SMS that arrived since the last scan and were never seen live (the phone was asleep or
     * the app was stopped), saves them, and sends the alert for recent ones. Does nothing without SMS access.
     */
    @Synchronized
    fun catchUp(context: Context): Int {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return 0
        val since = Prefs.lastScan(context)
        val now = System.currentTimeMillis()
        var newest = since
        var added = 0
        val cursor = runCatching {
            context.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), "date > ?", arrayOf(since.toString()), "date ASC")
        }.getOrNull() ?: return 0
        cursor.use { c ->
            val addressIdx = c.getColumnIndexOrThrow("address")
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                val date = c.getLong(dateIdx)
                newest = maxOf(newest, date)
                val body = c.getString(bodyIdx) ?: continue
                val recent = now - date < 48 * 3_600_000L
                if (process(context, body, c.getString(addressIdx), date, notify = recent) > 0) added++
            }
        }
        Prefs.setLastScan(context, newest)
        return added
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
