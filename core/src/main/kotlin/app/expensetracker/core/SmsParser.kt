package app.expensetracker.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

/**
 * Turns a bank / UPI SMS into a [ParsedTxn]. Returns null for anything that is not a
 * debit or credit alert (OTPs, balance alerts, promotions).
 */
object SmsParser {

    private val amountRegex =
        Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val otpRegex = Regex(
        """\b(otp|one[\s-]?time\s+(?:password|pin)|verification\s+code|do\s+not\s+share|never\s+share)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val promoRegex = Regex(
        """\b(pre[\s-]?approved|apply\s+now|offer|cashback\s+up\s?to|win\s|congratulations|click\s+(?:here|on)|t&c|loan\s+of)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val cardPaymentRegex = Regex(
        """(?:payment\b.*\b(?:received|credited)\b.*\bcredit\s*card|credit\s*card\b.*\bpayment\b.*\b(?:received|credited))""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val debitRegex =
        Regex("""\b(debited|debit|sent|spent|withdrawn|withdrawal|paid|purchase|dr)\b""", RegexOption.IGNORE_CASE)

    // "credit card" is a product name, not a credit alert.
    private val creditRegex =
        Regex("""\b(credited|credit(?!\s*card)|received|deposited|refund(?:ed)?)\b""", RegexOption.IGNORE_CASE)

    private val balanceContext = Regex("""(bal|balance|limit|avl|avail|available|outstanding)""", RegexOption.IGNORE_CASE)

    private val accountRegex = Regex(
        """(?:a/c|acct?|account|card)\s*(?:no\.?\s*)?(?:ending\s*(?:with\s*)?)?[*xX.\s-]*(\d{3,6})\b""",
        RegexOption.IGNORE_CASE,
    )

    private val refRegex =
        Regex("""\b(?:ref(?:erence)?(?:\s*(?:no|number))?\.?|utr|rrn)\s*[:\-]?\s*(\d{6,})""", RegexOption.IGNORE_CASE)

    // "ACH-DR-Groww-00008QHNQDMBX": the shop name, then the debit's own id.
    private val achRegex = Regex(
        """(?i)\bACH[-\s]?(?:DR|D)[-\s]+([A-Za-z][A-Za-z0-9 &.']{1,30}?)[-\s]+([A-Za-z0-9]{8,})\b""",
    )
    private val towardsRegex = Regex("""(?i)\btowards\s+([A-Za-z][A-Za-z0-9 &.']{1,30}?)\s+(?:for|of|with)\b""")
    private val mandateWord = Regex("""(?i)\b(nach|ecs|umrn|mandate)\b""")
    private val processedWord = Regex("""(?i)\b(successfully\s+processed|has\s+been\s+processed)\b""")

    private val toLineRegex = Regex("""(?im)^\s*to\s*:?\s+(.+?)\s*$""")
    private val toInlineRegex = Regex(
        """(?i)\bto\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|ref|upi|via|at)\b|\s*[.\n,]|$)""",
    )
    private val atRegex = Regex(
        """(?i)\b(?:at|@)\s+([A-Za-z0-9&'._ -]{2,30}?)(?=\s+(?:on|ref|via|using|\.)|\s*[.\n,]|$)""",
    )
    private val fromRegex = Regex(
        """(?i)\bfrom\s+(?:vpa\s+)?([A-Za-z0-9@._&' -]{2,40}?)(?=\s+(?:on|ref|upi|via)\b|\s*[.\n,]|$)""",
    )

    private val dateRegex = Regex("""\b(\d{1,2})[/-](\d{1,2}|[A-Za-z]{3})[/-](\d{4}|\d{2})\b""")

    private val banks = listOf(
        "HDFC" to "HDFC", "ICICI" to "ICICI", "SBI" to "SBI", "State Bank" to "SBI",
        "Axis" to "Axis", "Canara" to "Canara", "Kotak" to "Kotak", "PNB" to "PNB",
        "Punjab National" to "PNB", "Bank of Baroda" to "BoB", "BOB" to "BoB",
        "Union Bank" to "Union", "IDFC" to "IDFC", "Yes Bank" to "Yes Bank",
        "IndusInd" to "IndusInd", "Federal" to "Federal", "Paytm" to "Paytm", "IDBI" to "IDBI",
        "Bank of India" to "BoI", "Indian Bank" to "Indian Bank", "AU Small" to "AU",
    )

    fun parse(body: String, sender: String? = null): ParsedTxn? {
        val text = body.replace(' ', ' ').trim()
        if (text.isEmpty()) return null
        if (otpRegex.containsMatchIn(text)) return null

        val amountMatch = amountRegex.findAll(text).firstOrNull { m ->
            val before = text.substring((m.range.first - 25).coerceAtLeast(0), m.range.first)
            !balanceContext.containsMatchIn(before)
        } ?: return null
        val amountPaise = toPaise(amountMatch.groupValues[1]) ?: return null
        if (amountPaise <= 0) return null

        val isCardPayment = cardPaymentRegex.containsMatchIn(text)
        val debitAt = debitRegex.find(text)?.range?.first
        val creditAt = creditRegex.find(text)?.range?.first

        val type: TxnType = when {
            isCardPayment -> TxnType.CREDIT
            debitAt != null && (creditAt == null || debitAt < creditAt) -> TxnType.DEBIT
            creditAt != null -> TxnType.CREDIT
            else -> return null
        }

        // Marketing messages that mention an amount but are not a real transaction.
        if (!isCardPayment && promoRegex.containsMatchIn(text) && refRegex.find(text) == null &&
            accountRegex.find(text) == null
        ) return null

        val merchant = if (type == TxnType.DEBIT) debitMerchant(text) else creditSender(text)
        val kind = if (isCardPayment) TxnKind.CARD_BILL_PAYMENT else TxnKind.NORMAL
        val category = when {
            isCardPayment -> Categories.CARD_BILL
            else -> Categorizer.suggest(merchant, text, type)
        }

        return ParsedTxn(
            amountPaise = amountPaise,
            type = type,
            kind = kind,
            merchant = merchant,
            bank = detectBank(text, sender),
            account = accountRegex.find(text)?.groupValues?.get(1),
            ref = achRegex.find(text)?.groupValues?.get(2) ?: refRegex.find(text)?.groupValues?.get(1),
            epochDay = findDate(text)?.toEpochDay(),
            suggestedCategory = category,
            confirmation = type == TxnType.DEBIT && mandateWord.containsMatchIn(text) && processedWord.containsMatchIn(text),
        )
    }

    private fun toPaise(raw: String): Long? =
        runCatching { BigDecimal(raw.replace(",", "")).movePointRight(2).toLong() }.getOrNull()

    private fun clean(name: String?): String? {
        val n = name?.trim()?.trim('.', ',', '-', ' ')?.replace(Regex("""\s+"""), " ")
        // A name needs at least one letter; "to 917036165000" is a phone number, not a merchant.
        return if (n.isNullOrEmpty() || n.length < 2 || n.none { it.isLetter() }) null else n
    }

    private fun debitMerchant(text: String): String? {
        achRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        towardsRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        toLineRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        toInlineRegex.find(text)?.let { m ->
            val n = clean(m.groupValues[1])
            // "debited to A/C ..." style matches are the account, not a merchant.
            if (n != null && !n.contains("a/c", ignoreCase = true) && !n.matches(Regex("""(?i)(your|the)\b.*"""))) return n
        }
        atRegex.find(text)?.let { clean(it.groupValues[1])?.let { n -> return n } }
        return null
    }

    private fun creditSender(text: String): String? {
        val m = fromRegex.find(text) ?: return null
        val n = clean(m.groupValues[1]) ?: return null
        val looksLikeAccount = Regex("""(?i)(a/c|account|bank|card)""").containsMatchIn(n)
        return if (looksLikeAccount) null else n
    }

    private fun detectBank(text: String, sender: String?): String? {
        val haystack = text + " " + (sender ?: "")
        return banks.firstOrNull { (needle, _) ->
            Regex("""(?i)\b${Regex.escape(needle)}""").containsMatchIn(haystack)
        }?.second
    }

    private fun findDate(text: String): LocalDate? {
        for (m in dateRegex.findAll(text)) {
            val day = m.groupValues[1].toIntOrNull() ?: continue
            val monthRaw = m.groupValues[2]
            val month = monthRaw.toIntOrNull() ?: monthFromName(monthRaw) ?: continue
            var year = m.groupValues[3].toIntOrNull() ?: continue
            if (year < 100) year += 2000
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun monthFromName(name: String): Int? =
        Month.entries.firstOrNull { it.name.startsWith(name.uppercase()) }?.value
}
