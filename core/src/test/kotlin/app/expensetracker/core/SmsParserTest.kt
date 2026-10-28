package app.expensetracker.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SmsParserTest {

    private fun parse(body: String) = assertNotNull(SmsParser.parse(body), "expected a transaction in: $body")

    @Test
    fun hdfcUpiDebit() {
        val t = parse(
            """Sent Rs.110.00
From HDFC Bank A/C *1314
To FAMILY KITCHEN ICECREAMS
On 30/09/26
Ref 419755012736
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808""",
        )
        assertEquals(11000, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(TxnKind.NORMAL, t.kind)
        assertEquals("FAMILY KITCHEN ICECREAMS", t.merchant)
        assertEquals("HDFC", t.bank)
        assertEquals("1314", t.account)
        assertEquals("419755012736", t.ref)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), t.epochDay)
        assertEquals("Food", t.suggestedCategory)
    }

    @Test
    fun hdfcUpiToPerson() {
        val t = parse(
            """Sent Rs.15000.00
From HDFC Bank A/C *1854
To N MADHU BABU
On 30/09/26
Ref 643723303042
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808""",
        )
        assertEquals(1_500_000, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("N MADHU BABU", t.merchant)
        assertEquals("1854", t.account)
        assertNull(t.suggestedCategory)
    }

    @Test
    fun iciciCreditCardBillPayment() {
        val t = parse("Payment of Rs 8,718.38 has been received on your ICICI Bank Credit Card XX5009 through Bharat Bill Payment System on 30-SEP-26.")
        assertEquals(871_838, t.amountPaise)
        assertEquals(TxnKind.CARD_BILL_PAYMENT, t.kind)
        assertEquals(Categories.CARD_BILL, t.suggestedCategory)
        assertEquals("ICICI", t.bank)
        assertEquals("5009", t.account)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), t.epochDay)
    }

    @Test
    fun sbiCreditCardBillPaymentIgnoresAvailableLimit() {
        val t = parse("We have received payment of Rs.6,698.00 via BBPS & the same has been credited to your SBI Credit Card. Your available limit is Rs.198,140.17.")
        assertEquals(669_800, t.amountPaise)
        assertEquals(TxnKind.CARD_BILL_PAYMENT, t.kind)
        assertEquals("SBI", t.bank)
    }

    @Test
    fun axisCardChargesHavePhoneNumberButNoMerchant() {
        val t = parse(
            """Debit INR 354.00
Axis Bank A/c XX5530
30-09-26 12:02:56
Dr Card Charges GST ANNUAL
WhatsApp BAL to 917036165000
Query? Call 18001035577""",
        )
        assertEquals(35_400, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertNull(t.merchant)
        assertEquals("Axis", t.bank)
        assertEquals("5530", t.account)
        assertEquals(Categories.BANK_CHARGES, t.suggestedCategory)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), t.epochDay)
    }

    @Test
    fun canaraCreditUsesFirstAmountNotBalanceAndSmsDate() {
        val t = parse("An amount of INR 15,000.00 has been CREDITED to your account XXX494 on 09/12/2025.Total Avail.bal INR 21,642.00.- Canara Bank")
        assertEquals(1_500_000, t.amountPaise)
        assertEquals(TxnType.CREDIT, t.type)
        assertEquals("Canara", t.bank)
        assertEquals("494", t.account)
        assertEquals(LocalDate.of(2025, 12, 9).toEpochDay(), t.epochDay)
    }

    @Test
    fun creditCardSpendIsADebitEvenThoughTextSaysCreditCard() {
        val t = parse("Rs 1,299.00 spent on your HDFC Bank Credit Card XX4321 at AMAZON on 12-08-26.")
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals(TxnKind.NORMAL, t.kind)
        assertEquals("AMAZON", t.merchant)
        assertEquals("Shopping", t.suggestedCategory)
    }

    @Test
    fun otpsBalanceAlertsAndPromosAreIgnored() {
        assertNull(SmsParser.parse("123456 is your OTP for transaction of Rs.500.00 at SWIGGY. Do not share with anyone."))
        assertNull(SmsParser.parse("Your A/c XX1234 balance is Rs.5,000.00 as on 30-09-26."))
        assertNull(SmsParser.parse("Pre-approved personal loan of Rs.5,00,000 just for you! Apply now."))
        assertNull(SmsParser.parse("Hello, how are you?"))
    }

    @Test
    fun indianGroupedAmounts() {
        val t = parse("Rs.1,25,000.50 debited from A/c XX9999 on 01-01-26 to VPA rent@okhdfc. UPI Ref 100000000001")
        assertEquals(12_500_050, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("rent@okhdfc", t.merchant)
    }

    @Test
    fun categorizerFindsCommonMerchants() {
        assertEquals("Food", Categorizer.suggest("SWIGGY", "", TxnType.DEBIT))
        assertEquals("Transport", Categorizer.suggest("Uber India", "", TxnType.DEBIT))
        assertNull(Categorizer.suggest("RAMESH K", "", TxnType.DEBIT))
    }

    @Test
    fun axisAchDebitAlertNamesTheShopAndKeepsItsOwnId() {
        val sms = "Debit INR 2000.00\nAxis Bank A/c XX5530\n02-10-26 07:50:56\nACH-DR-Groww-00008QHNQDMBX\n" +
            "WhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002"
        val t = SmsParser.parse(sms)!!
        assertEquals(200_000L, t.amountPaise)
        assertEquals(TxnType.DEBIT, t.type)
        assertEquals("Groww", t.merchant)
        assertEquals("00008QHNQDMBX", t.ref)
        assertEquals("5530", t.account)
        assertEquals("Axis", t.bank)
        assertEquals(java.time.LocalDate.of(2026, 10, 2).toEpochDay(), t.epochDay)
        assertEquals(false, t.confirmation)
    }

    @Test
    fun nachConfirmationIsMarkedSoItIsNotCountedTwice() {
        val sms = "NACH debit towards Groww for INR 2,000.00 with UMRN UTIB7020809200003 has been successfully processed in A/c no. XX5530 today - Axis Bank"
        val t = SmsParser.parse(sms)!!
        assertEquals(200_000L, t.amountPaise)
        assertEquals("Groww", t.merchant)
        assertEquals("5530", t.account)
        assertEquals(true, t.confirmation)
    }
}
