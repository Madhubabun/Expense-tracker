package app.expensetracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.expensetracker.core.Reports
import app.expensetracker.data.Snapshot
import app.expensetracker.ui.MainActivity

/** Home screen widget: what you spent today and how much of the month's budget is used. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val t = Snapshot.today(context)
            val pct = if (t.budgetPaise > 0) Math.round(t.monthSpentPaise * 100f / t.budgetPaise) else 0
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.w_total, "₹" + Reports.formatRupees(t.spentTodayPaise).removeSuffix(".00"))
            views.setTextViewText(R.id.w_sub, "$pct% of monthly budget" + if (t.needCategory > 0) " · ${t.needCategory} to tag" else "")
            views.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            views.setOnClickPendingIntent(
                R.id.w_add,
                PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ADD, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(id, views)
        }
    }
}
