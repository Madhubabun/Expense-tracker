package app.expensetracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceParserTest {
    @Test
    fun spentOnChai() {
        val e = VoiceParser.parse("spent 250 rupees on chai")
        assertEquals(25_000L, e.amountPaise)
        assertEquals(TxnType.DEBIT, e.type)
        assertEquals("Chai", e.note)
        assertEquals("Food", e.category)
    }

    @Test
    fun groupedAmountAndDecimals() {
        assertEquals(120_050L, VoiceParser.parse("paid ₹1,200.50 for groceries").amountPaise)
    }

    @Test
    fun receivedSalaryIsIncome() {
        val e = VoiceParser.parse("received 50000 salary")
        assertEquals(5_000_000L, e.amountPaise)
        assertEquals(TxnType.CREDIT, e.type)
        assertEquals("Salary", e.category)
    }

    @Test
    fun noAmountGivesNull() {
        assertNull(VoiceParser.parse("coffee with friends").amountPaise)
    }
}
