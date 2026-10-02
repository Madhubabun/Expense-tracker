package app.expensetracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadarTest {
    private fun pt(day: Long, paise: Long, key: String = "netflix") = SpendPoint(key, key, day, paise)

    @Test
    fun monthlyPaymentWithPriceRiseIsOneSubscriptionMarkedUp() {
        val subs = Subscriptions.detect(listOf(pt(100, 19_900), pt(130, 19_900), pt(160, 24_900)), today = 170)
        assertEquals(1, subs.size)
        assertTrue(subs[0].priceUp)
        assertEquals(24_900, subs[0].amountPaise)
        assertEquals(190, subs[0].nextDay)
    }

    @Test
    fun yearlyPaymentIsFoundAndIrregularOnesAreNot() {
        val yearly = Subscriptions.detect(listOf(pt(0, 99_900, "prime"), pt(365, 99_900, "prime")), today = 400)
        assertEquals(1, yearly.size)
        assertEquals(99_900, yearly[0].yearlyPaise)
        assertTrue(Subscriptions.detect(listOf(pt(0, 100), pt(10, 100), pt(45, 100)), today = 50).isEmpty())
    }

    @Test
    fun calendarTakesDailySpendAndLandsEventsOnTheirDay() {
        val sub = Subscription("Netflix", 20_000, 20_000, 30, 90, 110)
        val events = MoneyCalendar.billEvents(listOf(sub), today = 100, days = 40)
        assertEquals(listOf(110L, 140L), events.map { it.day })
        val line = MoneyCalendar.project(100_000, 100, 12, 1_000, events)
        assertEquals(10, line.first { it.events.isNotEmpty() }.let { it.day - 100 }.toInt())
        assertEquals(100_000 - 12 * 1_000 - 20_000, line.last().balancePaise)
    }

    @Test
    fun personalityNeedsTenSpendsThenNamesTheStrongestTrait() {
        assertNull(Personality.describe(List(5) { SpendDot(0, 12, 50_000, "Food") }))
        val night = List(12) { SpendDot(it.toLong(), 23, 50_000, "Food") }
        assertEquals("Night owl spender", assertNotNull(Personality.describe(night)).title)
    }

    @Test
    fun whatIfCountsWholeMonths() {
        assertEquals(4, WhatIf.months(100_000, 30_000))
        assertNull(WhatIf.months(100_000, 0))
        assertEquals(20_000, WhatIf.cut(100_000, 20))
    }

    @Test
    fun atmWithdrawalIsFiledAsCashAndLeftOutOfSpending() {
        val parsed = assertNotNull(SmsParser.parse("Rs.5000.00 debited from A/c XX1234 on 02-10-26 by ATM withdrawal at MG Road. Avl bal Rs.20000", "AX-AXISBK"))
        assertEquals(Categories.CASH, parsed.suggestedCategory)
        assertTrue(Categories.CASH in Categories.excludedFromTotals)
    }
}
