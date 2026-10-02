package app.expensetracker.core

import java.math.BigDecimal

/** What was understood from a spoken or typed sentence such as "spent 250 rupees on chai". */
data class VoiceEntry(val amountPaise: Long?, val type: TxnType, val note: String, val category: String?)

object VoiceParser {
    private val incomeWords = listOf("received", "got", "salary", "credited", "earned", "income", "refund", "cashback", "bonus")
    private val fillers = setOf(
        "spent", "spend", "paid", "pay", "for", "on", "at", "to", "rupees", "rupee", "rs", "inr", "of", "my", "the", "a", "an",
        "received", "got", "from", "add", "expense", "income", "i", "in",
    )
    private val number = Regex("""(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d+)?|\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): VoiceEntry {
        val lower = text.lowercase().trim()
        val match = number.find(lower)
        val amount = match?.groupValues?.get(1)?.replace(",", "")
            ?.let { runCatching { BigDecimal(it).movePointRight(2).toLong() }.getOrNull() }
            ?.takeIf { it > 0 }
        val type = if (incomeWords.any { Regex("\\b$it\\b").containsMatchIn(lower) }) TxnType.CREDIT else TxnType.DEBIT
        val rest = (if (match != null) lower.removeRange(match.range) else lower)
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() && it !in fillers }
        val note = rest.joinToString(" ").replaceFirstChar { it.uppercase() }
        return VoiceEntry(amount, type, note, Categorizer.suggest(rest.joinToString(" "), lower, type))
    }
}
