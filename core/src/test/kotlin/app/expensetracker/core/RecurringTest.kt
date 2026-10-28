package app.expensetracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringTest {
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private fun p(key: String, day: Long, amt: Long) = SpendPoint(key, key, day, amt)

    @Test
    fun monthlyBillIsFoundAndNextDueIsAMonthAfterTheLast() {
        val points = listOf(p("netflix", day(1, 5), 64_900), p("netflix", day(2, 5), 64_900), p("netflix", day(3, 5), 64_900))
        val bills = Recurring.detect(points, day(3, 20))
        assertEquals(1, bills.size)
        assertEquals(64_900, bills[0].amountPaise)
        assertEquals(day(3, 5), bills[0].lastDay)
        assertTrue(bills[0].nextDay in day(4, 2)..day(4, 6))
    }

    @Test
    fun irregularSpendingAtTheSameShopIsNotABill() {
        val points = listOf(p("swiggy", day(1, 2), 30_000), p("swiggy", day(1, 9), 45_000), p("swiggy", day(1, 11), 28_000), p("swiggy", day(2, 20), 52_000))
        assertTrue(Recurring.detect(points, day(3, 1)).isEmpty())
    }

    @Test
    fun aBillThatStoppedLongAgoIsDropped() {
        val points = listOf(p("gym", day(1, 1), 100_000), p("gym", day(2, 1), 100_000))
        assertTrue(Recurring.detect(points, day(9, 1)).isEmpty())
    }

    @Test
    fun budgetLevels() {
        assertEquals(0, BudgetLevels.level(7_900, 10_000))
        assertEquals(80, BudgetLevels.level(8_000, 10_000))
        assertEquals(100, BudgetLevels.level(10_000, 10_000))
        assertEquals(0, BudgetLevels.level(5_000, 0))
    }
}
