package app.expensetracker.core

/** Guesses a category from the merchant name and SMS text. Returns null when unsure. */
object Categorizer {

    private val rules: List<Pair<String, List<String>>> = listOf(
        Categories.BANK_CHARGES to listOf(
            "charges", "annual fee", "amc", "sms alert", "gst", "penalty", "late fee", "processing fee",
        ),
        "Food" to listOf(
            "swiggy", "zomato", "kitchen", "restaurant", "cafe", "bakery", "hotel", "dominos", "pizza",
            "burger", "icecream", "ice cream", "biryani", "tiffin", "mess", "juice", "tea", "coffee", "sweets",
            "chai", "lunch", "dinner", "breakfast", "snack", "snacks",
        ),
        "Groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "instamart", "dmart", "supermarket", "grocery", "kirana",
            "provision", "vegetable", "fruits", "mart",
        ),
        "Transport" to listOf(
            "uber", "ola", "rapido", "irctc", "redbus", "metro", "petrol", "fuel", "fastag", "parking", "indian oil",
            "hpcl", "bpcl", "railway",
        ),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon", "lifestyle"),
        "Bills" to listOf(
            "electricity", "bescom", "tneb", "airtel", "jio", "vodafone", "bsnl", "recharge", "broadband",
            "gas", "water", "insurance", "lic", "postpaid", "dth", "tata play",
        ),
        "Entertainment" to listOf("netflix", "spotify", "hotstar", "bookmyshow", "prime video", "youtube", "pvr", "inox"),
        "Health" to listOf("pharmacy", "medical", "medplus", "apollo", "hospital", "clinic", "1mg", "pharmeasy", "diagnostic"),
    )

    fun suggest(merchant: String?, text: String, type: TxnType): String? {
        if (type == TxnType.CREDIT) {
            return if (Regex("""(?i)\bsalary\b""").containsMatchIn(text)) "Salary" else null
        }
        // Merchant name first, so "Sent Rs.110 ... To FAMILY KITCHEN" matches on the merchant.
        val fromMerchant = merchant?.lowercase()?.let { match(it) }
        if (fromMerchant != null) return fromMerchant
        // Bank charge messages have no merchant, so fall back to the message text.
        val lower = text.lowercase()
        return rules.firstOrNull { it.first == Categories.BANK_CHARGES }
            ?.let { (name, words) -> if (words.any { wordIn(lower, it) }) name else null }
    }

    private fun match(lowerName: String): String? =
        rules.firstOrNull { (_, words) -> words.any { wordIn(lowerName, it) } }?.first

    private fun wordIn(haystack: String, word: String): Boolean =
        if (word.length <= 4) Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(haystack) else haystack.contains(word)
}
