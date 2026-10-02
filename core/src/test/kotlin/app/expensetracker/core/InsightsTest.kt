package app.expensetracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightsTest {
    private val today = LocalDate.of(2026, 10, 20)
    private fun spend(day: Int, rupees: Long, cat: String, month: Int = 10) =
        ReportTxn(rupees * 100, TxnType.DEBIT, LocalDate.of(2026, month, day), cat)

    @Test
    fun suggestsTrimmingFlexibleCategories() {
        val txns = listOf(spend(2, 4000, "Food"), spend(3, 3000, "Shopping"), spend(4, 9000, "Rent"))
        val r = Insights.generate(txns, Period.MONTH, today, today = today)
        assertEquals(listOf("Food", "Shopping"), r.ideas.map { it.category })
        assertEquals(4000L * 100 * 15 / 100 + 3000L * 100 * 20 / 100, r.potentialPaise)
        assertTrue(r.insights.first().title.startsWith("You could save"))
    }

    @Test
    fun flagsCategoryThatJumped() {
        val txns = listOf(spend(5, 1000, "Shopping", month = 9), spend(5, 3000, "Shopping"))
        val r = Insights.generate(txns, Period.MONTH, today, today = today)
        assertTrue(r.insights.any { it.title == "Shopping is up 200%" })
    }

    @Test
    fun warnsWhenPaceBeatsBudget() {
        val txns = listOf(spend(3, 25000, "Rent"))
        val r = Insights.generate(txns, Period.MONTH, today, budgetPaise = 3_000_000, today = today)
        assertTrue(r.insights.any { it.title == "Heading over budget" })
    }

    @Test
    fun noSpendingGivesNoTips() {
        assertTrue(Insights.generate(emptyList(), Period.WEEK, today, today = today).insights.isEmpty())
    }
}
