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
import androidx.core.content.ContextCompat
import app.expensetracker.core.BudgetLevels
import app.expensetracker.core.Period
import app.expensetracker.core.Recurring
import app.expensetracker.core.Reports
import app.expensetracker.core.SpendPoint
import app.expensetracker.core.TxnKind
import app.expensetracker.core.TxnType
import app.expensetracker.data.Db
import app.expensetracker.data.Prefs
import app.expensetracker.data.Snapshot
import app.expensetracker.ui.MainActivity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Budget warnings, the 9 pm summary and bill reminders. Each one can be switched off in Settings. */
object Alerts {
    private const val CH_BUDGET = "budget"
    private const val CH_REMIND = "reminders"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun post(context: Context, id: Int, channel: String, title: String, text: String, openAdd: Boolean = false) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_ADD, openAdd),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun money(paise: Long) = "₹" + Reports.formatRupees(paise).removeSuffix(".00")

    /** Warns once at 80% and once at 100% of the monthly budget, and of each category budget. */
    fun checkBudgets(context: Context) {
        if (!Prefs.flag(context, Prefs.BUDGET_ALERTS, true)) return
        val today = LocalDate.now()
        val month = Reports.summarize(Snapshot.report(Snapshot.visible(context)), Period.MONTH, today)
        val monthName = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val stamp = today.year * 100 + today.monthValue

        val budget = Prefs.budgetPaise(context)
        val level = BudgetLevels.level(month.spentPaise, budget)
        if (level > Prefs.alertLevel(context, "overall-$stamp")) {
            Prefs.setAlertLevel(context, "overall-$stamp", level)
            post(
                context, 7001, CH_BUDGET,
                if (level >= 100) "$monthName budget used up" else "80% of your $monthName budget is gone",
                "${money(month.spentPaise)} spent of ${money(budget)}.",
            )
        }
        Db.get(context).categoryBudgets().forEach { (category, limit) ->
            val spent = month.byCategory.firstOrNull { it.category == category }?.spentPaise ?: 0L
            val lvl = BudgetLevels.level(spent, limit)
            val key = "cat-$category-$stamp"
            if (lvl > Prefs.alertLevel(context, key)) {
                Prefs.setAlertLevel(context, key, lvl)
                post(
                    context, 7100 + abs(category.hashCode() % 800), CH_BUDGET,
                    if (lvl >= 100) "$category budget used up" else "80% of your $category budget is gone",
                    "${money(spent)} spent of ${money(limit)} this month.",
                )
            }
        }
    }

    /** The 9 pm and 10 pm nudges. Tapping one opens the add sheet. */
    fun logReminder(context: Context, late: Boolean) {
        if (!Prefs.flag(context, Prefs.NIGHTLY_SUMMARY, true)) return
        if (late) post(context, 7003, CH_REMIND, "Anything left to add?", "Last call for today's spends.", openAdd = true)
        else post(context, 7002, CH_REMIND, "Log today's spends", "Tap to add what you spent today.", openAdd = true)
    }

    /** 9 am: the day before a repeat is due, and again on the day, unless its bank debit already arrived. */
    fun repeatReminders(context: Context) {
        if (!Prefs.flag(context, Prefs.REPEAT_REMINDERS, true)) return
        val today = LocalDate.now()
        app.expensetracker.data.Plans.upcoming(context, today, 1).filter { it.repeat.type == TxnType.DEBIT }.forEach { d ->
            val whenText = when {
                d.day == today -> "today"
                d.day == today.plusDays(1) -> "tomorrow"
                else -> "overdue"
            }
            if (whenText == "overdue") return@forEach
            val verb = "due"
            post(context, 7600 + (d.repeat.id % 400).toInt() * 2 + (if (d.day == today) 0 else 1), CH_REMIND, "${d.repeat.title} $verb $whenText", "${money(d.amountPaise)}")
        }
    }

    /** Bills are spotted from all history, including older spends that are hidden from the screens. */
    fun detectBills(context: Context, today: Long) = Recurring.detect(
        Db.get(context).all().filter { it.type == TxnType.DEBIT && it.kind == TxnKind.NORMAL && !it.merchant.isNullOrBlank() }
            .map { SpendPoint(it.merchant!!.lowercase(), it.merchant, it.epochDay, it.amountPaise) },
        today,
    )
}
