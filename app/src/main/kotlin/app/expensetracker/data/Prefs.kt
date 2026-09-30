package app.expensetracker.data

import android.content.Context

/** Small settings kept on the phone. The budget is in paise, like every other amount. */
object Prefs {
    private const val FILE = "settings"
    private const val BUDGET = "monthly_budget_paise"
    const val DEFAULT_BUDGET_PAISE = 3_000_000L

    fun budgetPaise(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(BUDGET, DEFAULT_BUDGET_PAISE)

    fun setBudgetPaise(context: Context, paise: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(BUDGET, paise).apply()
    }
}
