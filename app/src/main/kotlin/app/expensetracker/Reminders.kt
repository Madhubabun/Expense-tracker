package app.expensetracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Three small alarms a day and nothing running in between: 9 am repeat reminders, and the 9 pm and 10 pm
 * nudges to log the day's spends. Each one sets up the next one when it fires.
 */
object Reminders {
    private const val NINE_PM = 77
    private const val MORNING = 79
    private const val TEN_PM = 80

    fun schedule(context: Context) {
        // The 15-minute inbox scan of older versions is gone; make sure it is not left behind.
        runCatching {
            val old = PendingIntent.getBroadcast(
                context, 78, Intent().setClassName(context, "app.expensetracker.CatchUpReceiver"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
            if (old != null) { context.getSystemService(AlarmManager::class.java).cancel(old); old.cancel() }
        }
        arm(context, NINE_PM, 21, DailyReceiver::class.java)
        arm(context, TEN_PM, 22, NightReceiver::class.java)
        arm(context, MORNING, 9, RepeatRemindReceiver::class.java)
    }

    /** Sets the next run of one alarm, at [hour] o'clock today or tomorrow. Setting it again replaces it. */
    internal fun arm(context: Context, code: Int, hour: Int, target: Class<*>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, code, Intent(context, target), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 60_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Allowed to run while the phone dozes. Android may nudge it a little to save battery.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    internal fun run(context: Context, code: Int, hour: Int, target: Class<*>, work: () -> Unit) {
        // Queue tomorrow's alarm first, so one failure never ends the reminders.
        runCatching { arm(context, code, hour, target) }
        runCatching(work)
    }
}

/** 9 am each day: reminders for repeats due today or tomorrow. */
class RepeatRemindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.run(context, 79, 9, RepeatRemindReceiver::class.java) {
            Alerts.repeatReminders(context)
            app.expensetracker.TodayWidget.refresh(context)
        }
    }
}

/** 9 pm: the first nudge to log today's spends. */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.run(context, 77, 21, DailyReceiver::class.java) { Alerts.logReminder(context, late = false) }
    }
}

/** 10 pm: the last nudge. */
class NightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.run(context, 80, 22, NightReceiver::class.java) { Alerts.logReminder(context, late = true) }
    }
}

/** Alarms are forgotten when the phone restarts, so set them up again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Reminders.schedule(context)
    }
}
