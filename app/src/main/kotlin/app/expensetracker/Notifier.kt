package app.expensetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.expensetracker.core.Reports
import app.expensetracker.core.TxnType
import app.expensetracker.data.Db
import app.expensetracker.data.Txn
import app.expensetracker.ui.MainActivity

object Notifier {
    private const val CHANNEL = "transactions"
    const val EXTRA_TXN_ID = "txn_id"
    const val EXTRA_CATEGORY = "category"
    const val KEY_NOTE = "note"
    const val ACTION_CATEGORY = "app.expensetracker.SET_CATEGORY"
    const val ACTION_NOTE = "app.expensetracker.SET_NOTE"

    private val buttonIds = intArrayOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4)
    private val quickDefaults = listOf("Food", "Groceries", "Transport", "Shopping")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Transactions", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Asks for a category when a debit or credit SMS arrives"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Four category buttons to show: the current or suggested category first, then the usual ones. */
    private fun quickCategories(t: Txn): List<String> =
        (listOfNotNull(t.category.ifEmpty { null }) + quickDefaults).distinct().take(4)

    /**
     * Posts (or updates) the notification for one transaction. The body is a custom layout with four
     * category buttons (Android's standard action row stops at three) and a separate "Add comment" reply action.
     */
    fun show(context: Context, t: Txn, quiet: Boolean = false) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val sign = if (t.type == TxnType.DEBIT) "Debited" else "Credited"
        val who = t.merchant ?: t.bank ?: "Bank SMS"
        val headline = "$sign ₹${Reports.formatRupees(t.amountPaise)} · $who"
        val nid = t.id.toInt()

        val openApp = PendingIntent.getActivity(
            context,
            nid,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TXN_ID, t.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val categories = Db.get(context).categories().associateBy { it.name }
        val views = RemoteViews(context.packageName, R.layout.notif_categories)
        views.setTextViewText(R.id.title, if (t.category.isEmpty()) headline else "$headline · ${t.category} ✓")
        val quick = quickCategories(t)
        buttonIds.forEachIndexed { i, viewId ->
            val name = quick.getOrNull(i)
            if (name == null) {
                views.setViewVisibility(viewId, View.GONE)
            } else {
                val mark = if (name == t.category) "✓ " else ""
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, mark + (categories[name]?.emoji ?: "🧾") + " " + name)
                views.setOnClickPendingIntent(viewId, categoryIntent(context, t.id, name, nid * 10 + i))
            }
        }

        val remoteInput = RemoteInput.Builder(KEY_NOTE).setLabel("Comment").build()
        val noteIntent = PendingIntent.getBroadcast(
            context,
            nid * 10 + 9,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_NOTE).putExtra(EXTRA_TXN_ID, t.id),
            // RemoteInput needs a mutable PendingIntent so the typed text can be filled in.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headline)
            .setContentText(if (t.category.isEmpty()) "Pick a category" else t.category)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setCustomHeadsUpContentView(views)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(NotificationCompat.Action.Builder(0, "💬 Add comment", noteIntent).addRemoteInput(remoteInput).build())

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
