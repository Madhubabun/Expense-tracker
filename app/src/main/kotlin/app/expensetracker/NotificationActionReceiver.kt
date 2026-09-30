package app.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.expensetracker.data.Db

/** Handles the category buttons and the comment box in the transaction notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_TXN_ID, -1)
        if (id < 0) return
        val db = Db.get(context)
        val txn = db.get(id) ?: return

        when (intent.action) {
            Notifier.ACTION_CATEGORY -> {
                val category = intent.getStringExtra(Notifier.EXTRA_CATEGORY) ?: return
                db.setCategoryAndComment(id, category, txn.comment)
                db.get(id)?.let { Notifier.show(context, it, quiet = true) }
            }
            Notifier.ACTION_NOTE -> {
                val note = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_NOTE)?.toString()?.trim()
                if (!note.isNullOrEmpty()) db.setComment(id, note)
                // Closing the notification is the confirmation that the comment was saved.
                Notifier.cancel(context, id)
            }
        }
    }
}
