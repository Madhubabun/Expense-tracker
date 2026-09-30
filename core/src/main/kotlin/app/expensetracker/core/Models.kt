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

    val defaults = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills", "Entertainment",
        "Health", "Bank charges", "Salary", "Transfer", "Other",
    )

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
)
