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
        val pending = PendingIntent.getBroadcast(
            context, 77, Intent(context, DailyReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val first = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact, so Android may shift it a little to save battery. Scheduling again replaces the old alarm.
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Alerts.nightlySummary(context) }
        runCatching { Alerts.billReminders(context) }
        runCatching { TodayWidget.refresh(context) }
    }
}

/** Alarms are forgotten when the phone restarts, so set them up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Reminders.schedule(context)
        runCatching { TodayWidget.refresh(context) }
    }
}
