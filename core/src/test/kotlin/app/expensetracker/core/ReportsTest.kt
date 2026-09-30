package app.expensetracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun debit(paise: Long, date: LocalDate, category: String = "Food", kind: TxnKind = TxnKind.NORMAL) =
        ReportTxn(paise, TxnType.DEBIT, date, category, kind)

    @Test
    fun weekRunsMondayToSunday() {
        // 2026-09-30 is a Wednesday
        assertEquals(d(2026, 9, 28) to d(2026, 10, 4), Reports.range(Period.WEEK, d(2026, 9, 30)))
    }

    @Test
    fun monthAndYearRanges() {
        assertEquals(d(2026, 2, 1) to d(2026, 2, 28), Reports.range(Period.MONTH, d(2026, 2, 10)))
        assertEquals(d(2026, 1, 1) to d(2026, 12, 31), Reports.range(Period.YEAR, d(2026, 6, 1)))
    }

    @Test
    fun totalsSkipCardBillPaymentsAndTransfers() {
        val txns = listOf(
            debit(10_000, d(2026, 9, 30), "Food"),
            debit(20_000, d(2026, 9, 29), ""),
            debit(500_000, d(2026, 9, 29), Categories.TRANSFER),
            debit(900_000, d(2026, 9, 28), Categories.CARD_BILL, TxnKind.CARD_BILL_PAYMENT),
            ReportTxn(1_000_000, TxnType.CREDIT, d(2026, 9, 28), "Salary"),
            debit(99_999, d(2026, 8, 31), "Food"), // outside the month
        )
        val s = Reports.summarize(txns, Period.MONTH, d(2026, 9, 30))
        assertEquals(30_000, s.spentPaise)
        assertEquals(1_000_000, s.receivedPaise)
        assertEquals(listOf("Uncategorized" to 20_000L, "Food" to 10_000L), s.byCategory.map { it.category to it.spentPaise })
        assertEquals(30, s.bars.size)
        assertEquals(10_000, s.bars[29].spentPaise)
    }

    @Test
    fun yearHasTwelveBars() {
        val s = Reports.summarize(listOf(debit(100, d(2026, 3, 5)), debit(200, d(2026, 3, 9))), Period.YEAR, d(2026, 1, 1))
        assertEquals(12, s.bars.size)
        assertEquals(300, s.bars[2].spentPaise)
        assertEquals("Mar", s.bars[2].label)
    }

    @Test
    fun indianGrouping() {
        assertEquals("110.00", Reports.formatRupees(11_000))
        assertEquals("1,234.50", Reports.formatRupees(123_450))
        assertEquals("12,34,567.00", Reports.formatRupees(123_456_700))
        assertEquals("0.05", Reports.formatRupees(5))
    }
}
