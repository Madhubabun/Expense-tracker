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
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Daily summary and bills", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun post(context: Context, id: Int, channel: String, title: String, text: String) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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

    /** Tells you when a fresh spend is much bigger than what you usually pay at that shop or in that category. */
    fun checkUnusual(context: Context, txnId: Long) {
        if (!Prefs.flag(context, Prefs.UNUSUAL_ALERTS, true)) return
        val db = Db.get(context)
        val t = db.get(txnId) ?: return
        if (t.type != TxnType.DEBIT || t.kind != TxnKind.NORMAL) return
        val usual = app.expensetracker.core.Anomaly.usual(t.amountPaise, db.recentSpendAmounts(t.merchant, t.category, t.id)) ?: return
        post(
            context, 7400 + (t.id % 500).toInt(), CH_BUDGET,
            "Bigger than usual: ${money(t.amountPaise)}",
            "${t.merchant ?: t.category.ifEmpty { "This spend" }} is usually around ${money(usual)}. If that is right, ignore this.",
        )
    }

    /** The 9 pm note: today's total and how many spends still need a category. */
    fun nightlySummary(context: Context) {
        if (!Prefs.flag(context, Prefs.NIGHTLY_SUMMARY, true)) return
        val t = Snapshot.today(context)
        if (t.spentTodayPaise == 0L && t.needCategory == 0) return
        val text = when {
            t.needCategory > 0 -> "${t.needCategory} spends still need a category. Two taps and you're done."
            else -> "Everything is tagged. Nice."
        }
        post(context, 7002, CH_REMIND, "Today: ${money(t.spentTodayPaise)} spent", text)
    }

    /** Reminds you on the day before (and on the day of) a bill that comes around every month. */
    fun billReminders(context: Context) {
        if (!Prefs.flag(context, Prefs.BILL_REMINDERS, true)) return
        val today = LocalDate.now().toEpochDay()
        detectBills(context, today).filter { it.nextDay - today in 0..1 }.forEachIndexed { i, bill ->
            val whenText = if (bill.nextDay == today) "today" else "tomorrow"
            post(context, 7200 + i, CH_REMIND, "${bill.label} is due $whenText", "Usually ${money(bill.amountPaise)}.")
        }
    }

    /** Reminds you the day before and on the due date of a loan you owe or lent. */
    fun loanReminders(context: Context) {
        if (!Prefs.flag(context, Prefs.BILL_REMINDERS, true)) return
        val today = LocalDate.now().toEpochDay()
        app.expensetracker.data.Plans.loans(context).filter { it.dueDay > 0 && it.leftPaise > 0 && it.dueDay - today in 0..1 }.forEachIndexed { i, l ->
            val whenText = if (l.dueDay == today) "today" else "tomorrow"
            val title = if (l.borrowed) "Pay ${l.name} $whenText" else "${l.name} is due to repay you $whenText"
            post(context, 7300 + i, CH_REMIND, title, "${money(l.leftPaise)} left.")
        }
    }

    /** Bills are spotted from all history, including older spends that are hidden from the screens. */
    fun detectBills(context: Context, today: Long) = Recurring.detect(
        Db.get(context).all().filter { it.type == TxnType.DEBIT && it.kind == TxnKind.NORMAL && !it.merchant.isNullOrBlank() }
            .map { SpendPoint(it.merchant!!.lowercase(), it.merchant, it.epochDay, it.amountPaise) },
        today,
    )
}
