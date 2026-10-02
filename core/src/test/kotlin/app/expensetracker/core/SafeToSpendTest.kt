package app.expensetracker.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeToSpendTest {
    @Test
    fun repeatsAndSpendingComeOffIncomeAndTheRestIsSplitOverTheDaysLeft() {
        val r = SafeToSpend.compute(8_500_000, 0, 3_000_000, 4_900_000, 320_000, LocalDate.of(2026, 10, 2))
        assertEquals(3_280_000, r.leftPaise)
        assertEquals(30, r.daysLeft)
        assertEquals(109_333, r.perDayPaise)
        assertFalse(r.usingBudget)
    }

    @Test
    fun withoutAnyIncomeTheBudgetIsTheStartingPoint() {
        val r = SafeToSpend.compute(0, 0, 3_000_000, 500_000, 100_000, LocalDate.of(2026, 10, 31))
        assertTrue(r.usingBudget)
        assertEquals(2_400_000, r.leftPaise)
        assertEquals(1, r.daysLeft)
    }

    @Test
    fun overspendingShowsNegativeLeftButNeverANegativeDailyAmount() {
        val r = SafeToSpend.compute(1_000_000, 0, 0, 900_000, 300_000, LocalDate.of(2026, 10, 10))
        assertEquals(-200_000, r.leftPaise)
        assertEquals(0, r.perDayPaise)
    }

    @Test
    fun repeatMatchesNearItsDateAcrossMonthEndsAndForTheSameAmount() {
        assertTrue(RepeatPlan.matches(LocalDate.of(2026, 10, 3), 1_250_000, 5, 1_250_000, 0))
        assertTrue(RepeatPlan.matches(LocalDate.of(2026, 10, 30), 1_250_000, 1, 1_250_000, 0)) // 1 Nov is two days later
        assertFalse(RepeatPlan.matches(LocalDate.of(2026, 10, 15), 1_250_000, 5, 1_250_000, 0))
        assertFalse(RepeatPlan.matches(LocalDate.of(2026, 10, 5), 900_000, 5, 1_250_000, 0))
        assertTrue(RepeatPlan.matches(LocalDate.of(2026, 10, 10), 800_000, 10, 850_000, 10))
    }

    @Test
    fun shortMonthsUseTheirLastDayAndTitheFollowsIncome() {
        assertEquals(LocalDate.of(2026, 2, 28), RepeatPlan.occurrence(YearMonth.of(2026, 2), 31))
        assertEquals(850_000, RepeatPlan.amount(0, 10, 8_500_000))
        assertEquals(200_000, RepeatPlan.amount(200_000, 0, 8_500_000))
    }

    @Test
    fun sipsAreInvestmentsAndStayOutOfSpending() {
        assertEquals(Categories.INVEST, SmsParser.parse("Rs.2000.00 debited from A/c XX1234 ACH-DR-Groww-00008QHNQDMBX", "AX-AXISBK")?.suggestedCategory)
        assertTrue(Categories.INVEST in Categories.excludedFromTotals)
    }

    @Test
    fun dayPeriodIsOneDayWithASevenDayChart() {
        val d = LocalDate.of(2026, 10, 2)
        val t = listOf(ReportTxn(50_000, TxnType.DEBIT, d, "Food"), ReportTxn(20_000, TxnType.DEBIT, d.minusDays(1), "Food"), ReportTxn(900_000, TxnType.DEBIT, d, Categories.INVEST))
        val s = Reports.summarize(t, Period.DAY, d)
        assertEquals(50_000, s.spentPaise)
        assertEquals(900_000, s.investedPaise)
        assertEquals(7, s.bars.size)
        assertEquals(20_000, s.bars[5].spentPaise)
        assertEquals(d.minusDays(1), Reports.shift(Period.DAY, d, -1))
    }
}
