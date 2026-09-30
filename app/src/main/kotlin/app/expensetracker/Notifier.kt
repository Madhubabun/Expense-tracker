package app.expensetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Txn
import app.expensetracker.ui.MainActivity

object Notifier {
    private const val CHANNEL = "transactions"
    const val EXTRA_TXN_ID = "txn_id"
    const val EXTRA_CATEGORY = "category"
    const val KEY_NOTE = "note"
    const val ACTION_CATEGORY = "app.expensetracker.SET_CATEGORY"
    const val ACTION_NOTE = "app.expensetracker.SET_NOTE"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Transactions", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Asks for a category when a debit or credit SMS arrives"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts (or updates) the notification for one transaction with quick category and note actions. */
    fun show(context: Context, t: Txn, quiet: Boolean = false) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val sign = if (t.type == TxnType.DEBIT) "Debited" else "Credited"
        val title = "$sign ₹${Reports.formatRupees(t.amountPaise)}"
        val who = t.merchant ?: t.bank ?: "Bank SMS"
        val text = if (t.category.isEmpty()) "$who · pick a category" else "$who · ${t.category}"
        val nid = t.id.toInt()

        val openApp = PendingIntent.getActivity(
            context,
            nid,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TXN_ID, t.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (t.category.isEmpty() && t.type == TxnType.DEBIT) {
            val quick = listOf("Food", "Transport")
            quick.forEachIndexed { i, cat ->
                builder.addAction(0, cat, categoryIntent(context, t.id, cat, nid * 10 + i))
            }
        } else {
            builder.addAction(0, "Change category", openApp)
        }

        val remoteInput = RemoteInput.Builder(KEY_NOTE).setLabel("Comment").build()
        val noteIntent = PendingIntent.getBroadcast(
            context,
            nid * 10 + 9,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_NOTE).putExtra(EXTRA_TXN_ID, t.id),
            // RemoteInput needs a mutable PendingIntent so the typed text can be filled in.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(
            NotificationCompat.Action.Builder(0, "Add comment", noteIntent).addRemoteInput(remoteInput).build(),
        )

        NotificationManagerCompat.from(context).notify(nid, builder.build())
    }

    private fun categoryIntent(context: Context, id: Long, category: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_CATEGORY)
                .putExtra(EXTRA_TXN_ID, id)
                .putExtra(EXTRA_CATEGORY, category),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun cancel(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())
}
