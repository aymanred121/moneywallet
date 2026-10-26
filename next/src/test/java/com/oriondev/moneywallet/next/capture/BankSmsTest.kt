package com.oriondev.moneywallet.next.capture

import com.oriondev.moneywallet.next.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Structured bank messages: a type header plus "Key: Value" lines, English and Arabic. */
class BankSmsTest {
    private fun sms(text: String, sender: String = "Bank", rules: List<HeaderRule> = emptyList()): ParseResult.Payment =
        when (val r = PaymentParser.parse(sender, text, strict = true, rules = rules)) {
            is ParseResult.Payment -> r
            is ParseResult.Ignored -> fail("expected a payment, got ${r.reason} for:\n$text") as Nothing
        }

    private fun check(p: ParseResult.Payment, minor: Long, direction: Int, merchant: String?) {
        assertEquals("amount", minor, p.amountMinor)
        assertEquals("direction", direction, p.direction)
        assertEquals("merchant", merchant, p.merchant)
    }

    // the shape of the user's salary SMS (name and digits changed)
    @Test fun creditTransferLocalIsIncomeFromTheNamedSender() {
        val p = sms("Credit Transfer Local\nVia:IPS\nAmount:SR 12345.67\nTo:0000\nFrom: ACME CO.\nFrom:1234\n26/9/01 09:00")
        check(p, 1234567, Direction.INCOME, "Acme Co")
        assertEquals("SAR", p.currency)
        assertEquals("credit transfer", p.matchedType)
    }

    @Test fun sameMessageWithTheHeaderAsNotificationTitle() {
        // the in-app tester and some SMS apps put the first line in the title
        val r = PaymentParser.parse("Credit Transfer Local", "Via:IPS\nAmount:SR 12345.67\nTo:0000\nFrom: ACME CO.\nFrom:1234", strict = true)
        assertTrue(r is ParseResult.Payment && r.direction == Direction.INCOME)
    }

    @Test fun debitTransferLocalIsAnExpenseToTheNamedBeneficiary() = check(
        sms("Debit Transfer Local\nVia:IPS\nAmount:SR 1,500.00\nFrom:0000\nTo: MOHAMMED ALI\nTo:SA12****5678\n26/9/30 10:15"),
        150000, Direction.EXPENSE, "Mohammed Ali",
    )

    @Test fun posPurchaseEnglish() = check(
        sms("POS Purchase\nCard: mada **1234\nAmount: SAR 45.50\nAt: QUICKBITE\nBalance: SAR 10,000.00\n30/09/26 13:40"),
        4550, Direction.EXPENSE, "Quickbite",
    )

    @Test fun onlinePurchaseWithCurrencyAfterTheNumber() = check(
        sms("Online Purchase\nAmount: 89.00 SAR\nMerchant: AMAZON SA\nCard: Visa *9876"),
        8900, Direction.EXPENSE, "Amazon", // the " SA" country suffix is dropped
    )

    @Test fun arabicPurchase() = check(
        sms("شراء عبر نقاط البيع\nبطاقة: مدى **1234\nمبلغ: 25.00 ريال\nلدى: مطعم البرجر\nفي: 30/9/26 21:41"),
        2500, Direction.EXPENSE, "مطعم البرجر",
    )

    @Test fun arabicIncomingTransfer() = check(
        sms("حوالة محلية واردة\nعبر: سريع\nمبلغ: 2,000 ريال\nإلى: حساب **0000\nمن: شركة المثال\nفي: 30/9/26"),
        200000, Direction.INCOME, "شركة المثال",
    )

    @Test fun arabicOutgoingTransfer() = check(
        sms("حوالة داخلية صادرة\nمن: حساب **0000\nمبلغ: SAR 300\nإلى: سارة أحمد\nالرسوم: 0.50 ريال"),
        30000, Direction.EXPENSE, "سارة أحمد",
    )

    @Test fun arabicSalaryDeposit() = check(
        sms("إيداع راتب\nالمبلغ: 12,500.00 ر.س\nالحساب: **0000\nالرصيد: 13,000.00 ر.س"),
        1250000, Direction.INCOME, null,
    )

    @Test fun atmWithdrawalWithBareAmount() = check(
        sms("ATM Withdrawal\nAmount: 500\nCard: **1234\nBalance: 2,000.00"),
        50000, Direction.EXPENSE, null,
    )

    @Test fun purchaseRefundIsIncome() = check(
        sms("Purchase Refund\nAmount: SAR 89.00\nFrom: NOON\nCard: **1234"),
        8900, Direction.INCOME, "Noon",
    )

    @Test fun creditCardPurchaseIsAnExpense() = check(
        sms("Credit Card Purchase\nAmount: SAR 120.00\nAt: IKEA\nCard: **5555"),
        12000, Direction.EXPENSE, "Ikea",
    )

    @Test fun balanceAndFeeLinesAreNeverTheAmount() {
        val p = sms("Balance: SAR 9,999.00\nFees: SAR 1.00\nDebit Transfer\nAmount: SAR 250.00\nTo: LANDLORD")
        assertEquals(25000L, p.amountMinor)
    }

    @Test fun learnedRuleOverridesTheDictionary() {
        val text = "Mystery Movement\nAmount: SAR 75.00\nFrom: ACME CO."
        val p = sms(text, rules = listOf(HeaderRule("mystery movement", Direction.INCOME)))
        assertEquals(Direction.INCOME, p.direction)
        assertTrue(p.learned)
    }

    @Test fun ruleEncodingRoundTrips() {
        val r = HeaderRule("credit transfer local", Direction.INCOME)
        assertEquals(r, HeaderRule.decode(r.encode()))
        assertEquals(null, HeaderRule.decode("X|junk"))
    }

    @Test fun chatsStillIgnoredInSmsMode() {
        for (t in listOf("Payment for dinner 50 SAR", "I sent you 100 ريال", "can you pay me 30 SAR")) {
            assertTrue(t, PaymentParser.parse("Omar", t, strict = true) is ParseResult.Ignored)
        }
    }
}
