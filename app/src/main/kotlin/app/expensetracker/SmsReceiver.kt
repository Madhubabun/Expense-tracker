package app.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.expensetracker.data.SmsProcessor

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // goAsync keeps the app alive while we work, so the notification is not lost when the phone is busy.
        val pending = goAsync()
        Thread {
            try {
                // Long messages arrive in parts from the same sender; join them before parsing.
                messages.groupBy { it.originatingAddress }.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val time = parts.first().timestampMillis
                    // A busy database should not lose the alert, so try once more before giving up.
                    val ok = runCatching { SmsProcessor.process(context, body, sender, time, notify = true) }.isSuccess ||
                        runCatching { Thread.sleep(400); SmsProcessor.process(context, body, sender, time, notify = true) }.isSuccess
                    if (!ok) runCatching { SmsProcessor.catchUp(context) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
