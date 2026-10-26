package com.oriondev.moneywallet.next.capture

import com.oriondev.moneywallet.next.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Bank SMS: credit vs debit wording in the same message, refunds, and chats that mention money. */
class SmsDirectionTest {
    private fun sms(sender: String, text: String): ParseResult.Payment =
        when (val r = PaymentParser.parse(sender, text, strict = true)) {
            is ParseResult.Payment -> r
            is ParseResult.Ignored -> fail("expected a payment, got ${r.reason} for: $text") as Nothing
        }

    private fun direction(sender: String, text: String) = sms(sender, text).direction

    @Test fun debitMentioningReceivedByIsAnExpense() = assertEquals(
        Direction.EXPENSE,
        direction("Bank", "Outgoing transfer\nAmount: SAR 120.00\nFrom account **4411\nTo: Ahmed\nIt will be received by the beneficiary shortly"),
    )

    @Test fun debitedEnglishSmsIsAnExpense() {
        val p = sms("Bank", "Your account **1234 has been debited by SAR 45.00 at QUICKBITE on 29/09. Available balance SAR 1,000.00")
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals(4500L, p.amountMinor)
    }

    @Test fun creditedEnglishSmsIsIncome() {
        val p = sms("Bank", "Your account **1234 has been credited with SAR 12,500.00 from SALARY. Available balance SAR 13,500.00")
        assertEquals(Direction.INCOME, p.direction)
        assertEquals(1250000L, p.amountMinor)
    }

    @Test fun arabicIncomingTransferIsIncome() = assertEquals(
        Direction.INCOME,
        direction("Bank", "حوالة داخلية واردة\nعبر: حساب **4411\nمبلغ: SAR 500\nمن: محمد\n30/09 10:12"),
    )

    @Test fun arabicOutgoingTransferIsAnExpense() = assertEquals(
        Direction.EXPENSE,
        direction("Bank", "حوالة صادرة\nمن: حساب **4411\nمبلغ: 300 ريال\nإلى: سارة\nسيتم استلام المبلغ خلال دقائق"),
    )

    @Test fun arabicPurchaseIsAnExpense() {
        val p = sms("Bank", "شراء عبر نقاط البيع\nبطاقة: 3862;مدى-أبل باي\nمبلغ: SAR 25.00\nلدى: QUICKBITE\nفي: 29-09-26 21:41")
        assertEquals(Direction.EXPENSE, p.direction)
        assertEquals("Quickbite", p.merchant)
    }

    @Test fun refundIsIncomeEvenAfterPurchaseWording() {
        assertEquals(Direction.INCOME, direction("Bank", "Purchase refund\nCard **7788\nSAR 89.00 from NOON"))
        assertEquals(Direction.INCOME, direction("Bank", "عملية شراء - استرداد\nبطاقة مدى **7788\nمبلغ: 89 ريال"))
    }

    @Test fun titleWinsOverBody() = assertEquals(
        Direction.INCOME,
        direction("Credit alert", "SAR 200.00 to account **9900 from Fahad for payment of dinner"),
    )

    @Test fun chatsMentioningMoneyAreIgnored() {
        for (text in listOf("can you pay me 50 SAR tomorrow?", "I sent you 100 ريال", "دفعت 30 ريال على الغداء")) {
            val r = PaymentParser.parse("Omar", text, strict = true)
            assertTrue("$text -> $r", r is ParseResult.Ignored)
        }
    }

    @Test fun appNotificationsStayLenient() {
        // not an SMS: Google Wallet's short format has no bank context and must still be captured
        assertTrue(PaymentParser.parse("Starbucks", "SAR 18.50 with Visa ••1234") is ParseResult.Payment)
    }
}
