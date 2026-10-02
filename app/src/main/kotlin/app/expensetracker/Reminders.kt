package app.expensetracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Wakes the app once a day at about 9 pm for the summary and bill reminders. */
object Reminders {
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        // Already armed: setting them again on every launch would keep pushing the next run back.
        fun armed(code: Int, target: Class<*>) = PendingIntent.getBroadcast(context, code, Intent(context, target), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE) != null
        if (armed(77, DailyReceiver::class.java) && armed(78, CatchUpReceiver::class.java) && armed(79, RepeatRemindReceiver::class.java)) return
        val pending = PendingIntent.getBroadcast(
            context, 77, Intent(context, DailyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val first = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact, so Android may shift it a little to save battery. Scheduling again replaces the old alarm.
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
        // Every 15 minutes, check the inbox for bank messages that were missed while the phone was asleep.
        val scan = PendingIntent.getBroadcast(
            context, 78, Intent(context, CatchUpReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val morning = PendingIntent.getBroadcast(
            context, 79, Intent(context, RepeatRemindReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nineAm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, nineAm.timeInMillis, AlarmManager.INTERVAL_DAY, morning)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, scan)
    }
}

/** 9 am each day: reminders for repeats due today or tomorrow. */
class RepeatRemindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runCatching { app.expensetracker.data.SmsProcessor.catchUp(context) }
                runCatching { Alerts.repeatReminders(context) }
            } finally { pending.finish() }
        }.start()
    }
}

class CatchUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try { runCatching { app.expensetracker.data.SmsProcessor.catchUp(context) } } finally { pending.finish() }
        }.start()
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runCatching { app.expensetracker.data.SmsProcessor.catchUp(context) }
                runCatching { Alerts.nightlySummary(context) }
                runCatching { Alerts.billReminders(context) }
                runCatching { Alerts.loanReminders(context) }
                runCatching { app.expensetracker.data.Plans.applyRepeats(context) }
                runCatching { TodayWidget.refresh(context) }
            } finally { pending.finish() }
        }.start()
    }
}

/** Alarms are forgotten when the phone restarts, so set them up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Reminders.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                runCatching { app.expensetracker.data.SmsProcessor.catchUp(context) }
                runCatching { TodayWidget.refresh(context) }
            } finally { pending.finish() }
        }.start()
    }
}
