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
        // Long messages arrive in parts from the same sender; join them before parsing.
        messages.groupBy { it.originatingAddress }.forEach { (sender, parts) ->
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            val time = parts.first().timestampMillis
            runCatching { SmsProcessor.process(context, body, sender, time, notify = true) }
        }
    }
}
