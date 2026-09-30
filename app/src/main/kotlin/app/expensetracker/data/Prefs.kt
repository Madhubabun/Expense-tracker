package app.expensetracker.data

import android.content.Context

/** Small settings kept on the phone. The budget is in paise, like every other amount. */
object Prefs {
    private const val FILE = "settings"
    private const val BUDGET = "monthly_budget_paise"
    const val DEFAULT_BUDGET_PAISE = 3_000_000L

    private const val START_DAY = "tracking_start_epoch_day"

    /**
     * The first day the app counts. It is set to the day the app is first opened, so your old SMS history is
     * left out unless you choose to show it. 0 means "show everything".
     */
    fun startDay(context: Context): Long {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(START_DAY)) prefs.edit().putLong(START_DAY, java.time.LocalDate.now().toEpochDay()).apply()
        return prefs.getLong(START_DAY, 0L)
    }

    fun setStartDay(context: Context, epochDay: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(START_DAY, epochDay).apply()
    }

    fun budgetPaise(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(BUDGET, DEFAULT_BUDGET_PAISE)

    fun setBudgetPaise(context: Context, paise: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(BUDGET, paise).apply()
    }
}
