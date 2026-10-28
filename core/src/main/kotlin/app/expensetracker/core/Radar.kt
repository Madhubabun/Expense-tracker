package app.expensetracker.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** A payment that comes round again: a subscription, SIP or EMI. [oldAmountPaise] is the payment before the latest one. */
data class Subscription(
    val label: String,
    val amountPaise: Long,
    val oldAmountPaise: Long,
    val everyDays: Int,
    val lastDay: Long,
    val nextDay: Long,
) {
    val priceUp: Boolean get() = oldAmountPaise > 0 && amountPaise * 100 >= oldAmountPaise * 105
    val yearlyPaise: Long get() = amountPaise * 365 / everyDays.coerceAtLeast(1)
}

object Subscriptions {
    /**
     * Payments to the same place that repeat monthly (26 to 35 days apart) or yearly (355 to 375 days), looking
     * at the latest four. Amounts may drift up to 60% so a price rise still counts as the same subscription.
     * Ones that are more than five days overdue are dropped as probably cancelled.
     */
    fun detect(points: List<SpendPoint>, today: Long): List<Subscription> =
        points.groupBy { it.key }.mapNotNull { (_, all) ->
            val seq = all.sortedBy { it.epochDay }.distinctBy { it.epochDay }.takeLast(4)
            if (seq.size < 2) return@mapNotNull null
            val gaps = seq.zipWithNext { a, b -> (b.epochDay - a.epochDay).toInt() }
            val monthly = gaps.all { it in 26..35 }
            val yearly = gaps.all { it in 355..375 }
            if (!monthly && !yearly) return@mapNotNull null
            val amounts = seq.map { it.amountPaise }
            if (amounts.max() > amounts.min() * 1.6) return@mapNotNull null
            val every = gaps.average().roundToInt()
            val last = seq.last()
            Subscription(last.label, last.amountPaise, seq[seq.size - 2].amountPaise, every, last.epochDay, last.epochDay + every)
        }.filter { it.nextDay >= today - 5 }.sortedBy { it.nextDay }
}

/** One thing expected to happen on a day: [deltaPaise] is negative for money going out. */
data class CalendarEvent(val day: Long, val label: String, val deltaPaise: Long)

data class DayPoint(val day: Long, val balancePaise: Long, val events: List<CalendarEvent>)

object MoneyCalendar {
    /**
     * Day by day balance for the next [days] days: every day [dailySpendPaise] of ordinary spending is taken off,
     * and the known [events] (bills, EMIs, salary) land on their day. Day 1 is tomorrow.
     */
    fun project(startPaise: Long, today: Long, days: Int, dailySpendPaise: Long, events: List<CalendarEvent>): List<DayPoint> {
        var balance = startPaise
        return (1..days).map { i ->
            val day = today + i
            val todays = events.filter { it.day == day }
            balance -= dailySpendPaise
            balance += todays.sumOf { it.deltaPaise }
            DayPoint(day, balance, todays)
        }
    }

    /** Subscription and bill payments falling inside the window, repeated if they come round more than once. */
    fun billEvents(subs: List<Subscription>, today: Long, days: Int): List<CalendarEvent> =
        subs.flatMap { s ->
            generateSequence(s.nextDay) { it + s.everyDays }.takeWhile { it <= today + days }.filter { it > today }
                .map { CalendarEvent(it, s.label, -s.amountPaise) }.toList()
        }
}

/** One spend, reduced to what the spending personality needs. */
data class SpendDot(val epochDay: Long, val hour: Int, val amountPaise: Long, val category: String)

data class Persona(val emoji: String, val title: String, val facts: List<String>)

object Personality {
    /** Needs at least ten spends to say anything. The first matching trait names you; every trait is listed as a fact. */
    fun describe(spends: List<SpendDot>): Persona? {
        if (spends.size < 10) return null
        val n = spends.size.toDouble()
        val weekend = spends.count { java.time.LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value >= 6 } / n
        val night = spends.count { it.hour >= 22 || it.hour < 4 } / n
        val small = spends.count { it.amountPaise < 20_000 } / n
        val byCat = spends.filter { it.category.isNotEmpty() }.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amountPaise } }
        val total = spends.sumOf { it.amountPaise }.coerceAtLeast(1)
        val top = byCat.maxByOrNull { it.value }
        val topShare = top?.let { it.value.toDouble() / total } ?: 0.0
        val facts = buildList {
            add("${(weekend * 100).roundToInt()}% of your spends happen on weekends")
            add("${(night * 100).roundToInt()}% happen late at night (10 pm to 4 am)")
            add("${(small * 100).roundToInt()}% are under ₹200")
            if (top != null) add("${top.key} takes ${(topShare * 100).roundToInt()}% of your money")
        }
        return when {
            night >= 0.2 -> Persona("🦉", "Night owl spender", facts)
            weekend >= 0.45 -> Persona("🎉", "Weekend spender", facts)
            small >= 0.6 -> Persona("☕", "Small sips, they add up", facts)
            top != null && topShare >= 0.4 -> Persona("🎯", "${top.key} lover", facts)
            else -> Persona("⚖️", "Balanced spender", facts)
        }
    }
}

/** How a monthly saving changes the time to reach a goal. */
object WhatIf {
    /** Whole months needed to save [leftPaise] at [monthlyPaise] a month, or null if nothing is saved. */
    fun months(leftPaise: Long, monthlyPaise: Long): Int? =
        if (monthlyPaise <= 0) null else ((leftPaise + monthlyPaise - 1) / monthlyPaise).toInt()

    fun cut(monthlyCategoryPaise: Long, percent: Int): Long = monthlyCategoryPaise * percent.coerceIn(0, 100) / 100
}
