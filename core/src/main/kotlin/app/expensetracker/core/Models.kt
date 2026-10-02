package app.expensetracker.core

enum class TxnType { DEBIT, CREDIT }

enum class TxnKind {
    NORMAL,

    /** Payment made towards a credit card bill. Not income, and not spending either. */
    CARD_BILL_PAYMENT,
}

/** Category names the app treats specially. */
object Categories {
    const val TRANSFER = "Transfer"
    const val CARD_BILL = "Card bill payment"
    const val BANK_CHARGES = "Bank charges"
    const val UNCATEGORIZED = ""

    /** Categories that ship with the app. Colours are ARGB. People add their own on top of these. */
    data class Builtin(val name: String, val emoji: String, val argb: Long)

    val builtins = listOf(
        Builtin("Food", "🍜", 0xFFFF5C7A),
        Builtin("Groceries", "🛒", 0xFFB6FF5C),
        Builtin("Transport", "🚕", 0xFF2EF2E0),
        Builtin("Shopping", "🛍️", 0xFFB18CFF),
        Builtin("Bills", "💡", 0xFFFFB938),
        Builtin("Entertainment", "🎬", 0xFFFF4FD8),
        Builtin("Health", "💊", 0xFF4DA3FF),
        Builtin(BANK_CHARGES, "🏦", 0xFFB0AEC4),
        Builtin("Salary", "💰", 0xFF5CE08A),
        Builtin(TRANSFER, "🔁", 0xFF8F8DA2),
        Builtin(CARD_BILL, "💳", 0xFF8F8DA2),
        Builtin("Other", "🧾", 0xFFB0AEC4),
    )

    val defaults = builtins.map { it.name }

    /** Categories that are left out of spending and income totals. */
    val excludedFromTotals = setOf(TRANSFER, CARD_BILL)
}

data class ParsedTxn(
    val amountPaise: Long,
    val type: TxnType,
    val kind: TxnKind,
    val merchant: String?,
    val bank: String?,
    /** Last digits of the account or card, for example "1314". */
    val account: String?,
    /** UPI reference / UTR number. Used to avoid saving the same SMS twice. */
    val ref: String?,
    /** Transaction date written in the SMS, as days since 1970-01-01. Null if none was found. */
    val epochDay: Long?,
    val suggestedCategory: String?,
    /**
     * True for the second notice banks send about a mandate debit ("NACH debit towards X ... successfully
     * processed"). It repeats a debit that already had its own alert, so it must not count again.
     */
    val confirmation: Boolean = false,
)
