package com.oriondev.moneywallet.next.capture

import com.oriondev.moneywallet.next.data.db.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PaymentParserTest {

    private fun pay(title: String, text: String, fallback: String = "SAR"): ParseResult.Payment =
        when (val r = PaymentParser.parse(title, text, fallback)) {
            is ParseResult.Payment -> r
            is ParseResult.Ignored -> fail("expected a payment, got ignored (${r.reason}) for: $title / $text") as Nothing
        }

    private fun ignored(title: String, text: String): IgnoreReason =
        when (val r = PaymentParser.parse(title, text)) {
            is ParseResult.Ignored -> r.reason
            is ParseResult.Payment -> fail("expected ignored, got $r for: $title / $text") as Nothing
        }

    private fun assertPayment(p: ParseResult.Payment, minor: Long, iso: String, merchant: String?, direction: Int) {
        assertEquals("amount", minor, p.amountMinor)
        assertEquals("currency", iso, p.currency)
        assertEquals("merchant", merchant, p.merchant)
        assertEquals("direction", direction, p.direction)
    }

    // ---------------- Google Wallet / Samsung Wallet ----------------

    @Test fun googleWalletMerchantInTitle() {
        val p = pay("Starbucks", "SAR 18.50 with Visa ••1234")
        assertPayment(p, 1850, "SAR", "Starbucks", Direction.EXPENSE)
        assertTrue(p.confidence >= 0.75f)
    }

    @Test fun googleWalletUsdCardMaskIsNotTheAmount() =
        assertPayment(pay("Blue Bottle Coffee", "\$6.25 with Mastercard •• 4821"), 625, "USD", "Blue Bottle Coffee", Direction.EXPENSE)

    @Test fun googleWalletGenericTitleUsesAtMerchant() =
        assertPayment(pay("Google Wallet", "You paid SAR 32.00 at Jarir Bookstore"), 3200, "SAR", "Jarir Bookstore", Direction.EXPENSE)

    @Test fun samsungWalletEuro() =
        assertPayment(pay("Samsung Wallet", "Payment of €12,90 at CARREFOUR PARIS"), 1290, "EUR", "Carrefour Paris", Direction.EXPENSE)

    // ---------------- Arabic bank alerts ----------------

    @Test fun arabicPosPurchase() =
        assertPayment(pay("", "شراء عبر نقاط البيع\nمبلغ: 45.00 ريال\nلدى: STARBUCKS"), 4500, "SAR", "Starbucks", Direction.EXPENSE)

    @Test fun arabicPurchaseWithBalanceLineAfter() {
        val p = pay("الراجحي", "شراء إنترنت\nبطاقة: 4321 مدى\nمبلغ: SAR 1,250.00\nلدى: NOON.COM\nالرصيد: 8,400.75 ريال")
        assertPayment(p, 125000, "SAR", "Noon.com", Direction.EXPENSE)
    }

    @Test fun arabicBalanceBeforeAmountIsSkipped() =
        assertPayment(pay("", "الرصيد المتاح 3,000.00 ر.س\nتم خصم 99.00 ر.س لدى STC"), 9900, "SAR", "STC", Direction.EXPENSE)

    @Test fun arabicIndicDigitsAndDecimalSeparator() =
        assertPayment(pay("", "شراء بمبلغ ١٢٣٫٥٠ ريال لدى مطعم البرجر"), 12350, "SAR", "مطعم البرجر", Direction.EXPENSE)

    @Test fun arabicIndicThousandsSeparator() =
        assertPayment(pay("", "سحب نقدي\nالمبلغ: ١٬٥٠٠ ر.س"), 150000, "SAR", null, Direction.EXPENSE)

    @Test fun arabicRefundIsIncome() =
        assertPayment(pay("", "استرداد مبلغ 75.00 ريال من AMAZON.SA"), 7500, "SAR", "Amazon.sa", Direction.INCOME)

    @Test fun arabicIncomingTransferIsIncome() =
        assertPayment(pay("", "حوالة واردة\nمبلغ: 2,000.00 ريال\nمن: محمد أحمد"), 200000, "SAR", "محمد أحمد", Direction.INCOME)

    @Test fun arabicDepositIsIncome() =
        assertPayment(pay("", "إيداع راتب\nمبلغ: SAR 12,500.00\nالرصيد: SAR 14,000.00"), 1250000, "SAR", null, Direction.INCOME)

    @Test fun arabicWithdrawalIsExpense() =
        assertPayment(pay("بنك الرياض", "سحب من الصراف الآلي بمبلغ 500 ريال سعودي"), 50000, "SAR", "الصراف الآلي", Direction.EXPENSE)

    @Test fun arabicOutgoingTransferMerchantIsBeneficiary() =
        assertPayment(pay("", "حوالة صادرة بمبلغ 300 ر.س إلى خالد العتيبي"), 30000, "SAR", "خالد العتيبي", Direction.EXPENSE)

    @Test fun fromAccountIsNotAMerchant() =
        assertPayment(pay("", "تم خصم مبلغ 60 ريال من حسابك لدى مطعم البيك"), 6000, "SAR", "مطعم البيك", Direction.EXPENSE)

    // ---------------- currency tokens ----------------

    @Test fun srPrefix() = assertPayment(pay("", "Purchase of SR 27.50 at Kudu"), 2750, "SAR", "Kudu", Direction.EXPENSE)

    @Test fun riyalSignGlyph() = assertEquals("SAR", pay("Tamimi Markets", "⃁ 210.40 with mada ••9911").currency)

    @Test fun sarThousands() = assertEquals(125000L, pay("", "Purchase: SAR 1,250.00 at IKEA").amountMinor)

    @Test fun amountAfterCurrencyCode() =
        assertPayment(pay("", "45.50 SAR spent at Dunkin"), 4550, "SAR", "Dunkin", Direction.EXPENSE)

    @Test fun usdCode() = assertEquals("USD" to 1999L, pay("", "USD 19.99 charged at NETFLIX.COM").let { it.currency to it.amountMinor })

    @Test fun aedArabic() = assertEquals("AED" to 8000L, pay("", "شراء بقيمة 80 درهم لدى كارفور").let { it.currency to it.amountMinor })

    @Test fun aedCode() = assertEquals("AED", pay("", "AED 150.00 debited at DUBAI MALL").currency)

    @Test fun gbpSign() = assertEquals("GBP" to 450L, pay("Pret A Manger", "£4.50 with Visa ••1234").let { it.currency to it.amountMinor })

    @Test fun egpCode() = assertEquals("EGP" to 35000L, pay("", "Paid EGP 350 at Talabat").let { it.currency to it.amountMinor })

    @Test fun egpArabic() = assertEquals("EGP", pay("", "تم خصم 200 جنيه لدى فودافون").currency)

    @Test fun kuwaitiDinarHasThreeDecimals() = assertEquals("KWD" to 12500L, pay("", "KWD 12.500 paid at Alshaya").let { it.currency to it.amountMinor })

    @Test fun yenHasNoDecimals() = assertEquals("JPY" to 1250L, pay("Lawson", "¥1,250 with Visa ••1234").let { it.currency to it.amountMinor })

    @Test fun omaniRialIsDetected() = assertEquals("OMR", pay("", "شراء بمبلغ 5.250 ريال عماني").currency)

    @Test fun labelledAmountWithoutCurrencyUsesFallback() {
        val p = pay("", "عملية شراء\nمبلغ: 88.00\nلدى: BURGERHOUSE", fallback = "SAR")
        assertPayment(p, 8800, "SAR", "Burgerhouse", Direction.EXPENSE)
        assertTrue(p.confidence < pay("", "عملية شراء\nمبلغ: 88.00 ريال\nلدى: BURGERHOUSE").confidence)
    }

    // ---------------- English banks and wallets ----------------

    @Test fun englishBankAlertWithAvailableBalance() =
        assertPayment(
            pay("Bank", "POS purchase of SAR 64.25 at CITY MART SA on 12/05 14:32. Available balance: SAR 5,320.10"),
            6425, "SAR", "City Mart", Direction.EXPENSE,
        )

    @Test fun balanceFirstThenAmount() =
        assertEquals(4000L, pay("", "Available balance SAR 900.00 after a purchase of SAR 40.00 at Nahdi").amountMinor)

    @Test fun receivedIsIncome() =
        assertPayment(pay("Bank", "You received SAR 500.00 from Ahmed Ali"), 50000, "SAR", "Ahmed Ali", Direction.INCOME)

    @Test fun refundIsIncome() =
        assertPayment(pay("", "Refund of SAR 120.00 from Namshi has been credited to your card"), 12000, "SAR", "Namshi", Direction.INCOME)

    @Test fun creditCardWordIsNotIncome() =
        assertEquals(Direction.EXPENSE, pay("", "Credit card purchase SAR 75.00 at Hungerstation").direction)

    @Test fun debitedIsExpense() =
        assertPayment(pay("", "Your account was debited by SAR 230.00 to City Electricity"), 23000, "SAR", "City Electricity", Direction.EXPENSE)

    @Test fun timeAfterAtIsNotAMerchant() = assertNull(pay("Alert", "SAR 15.00 spent at 10:45 PM").merchant)

    @Test fun feeIsNotTheAmount() = assertEquals(20000L, pay("", "Fee SAR 5.00. Transfer of SAR 200.00 to Sara").amountMinor)

    @Test fun cashbackOnAPurchaseStaysExpense() =
        assertEquals(Direction.EXPENSE, pay("", "Purchase of SAR 50.00 at Quickbite. You earned cashback!").direction)

    // ---------------- ignored ----------------

    @Test fun englishOtpIgnored() = assertEquals(IgnoreReason.OTP, ignored("", "Your OTP for the purchase of SAR 45.00 at Amazon is 482913. Do not share it."))

    @Test fun arabicOtpIgnored() = assertEquals(IgnoreReason.OTP, ignored("", "رمز التحقق لعملية شراء بمبلغ 45 ريال: 7731"))

    @Test fun verificationCodeIgnored() = assertEquals(IgnoreReason.OTP, ignored("Wallet", "Your verification code is 5521"))

    @Test fun englishPromoIgnored() = assertEquals(IgnoreReason.PROMOTION, ignored("", "Get 20% off at Jarir with your mada card! Offer valid until 30/9"))

    @Test fun arabicPromoIgnored() = assertEquals(IgnoreReason.PROMOTION, ignored("", "استمتع بخصم 30% على مشترياتك من نون لفترة محدودة"))

    @Test fun arabicDiscountPercentIgnored() = assertEquals(IgnoreReason.PROMOTION, ignored("", "خصم 15% عند الدفع بمدى في هايبر بنده"))

    @Test fun declinedIgnored() = assertEquals(IgnoreReason.DECLINED, ignored("", "Transaction of SAR 300.00 at XCITE was declined: insufficient funds"))

    @Test fun arabicDeclinedIgnored() = assertEquals(IgnoreReason.DECLINED, ignored("", "عملية شراء مرفوضة بمبلغ 99 ريال"))

    @Test fun balanceOnlyIsNotAPayment() = assertEquals(IgnoreReason.NO_AMOUNT, ignored("", "رصيدك الحالي 4,500.00 ريال"))

    @Test fun noAmountIgnored() = assertEquals(IgnoreReason.NO_AMOUNT, ignored("Bank", "Your card has been activated"))

    // ---------------- dedupe ----------------

    @Test fun dedupeKeyIgnoresWhitespaceCaseAndDigitScript() {
        val a = PaymentParser.dedupeKey("com.bank", "Alert", "شراء بمبلغ ١٢ ريال", 1_200_000)
        val b = PaymentParser.dedupeKey("com.bank", "alert", "شراء  بمبلغ 12 ريال", 1_220_000)
        assertEquals(a, b)
        assertNotEquals(a, PaymentParser.dedupeKey("com.other", "Alert", "شراء بمبلغ 12 ريال", 1_200_000))
        assertNotEquals(a, PaymentParser.dedupeKey("com.bank", "Alert", "شراء بمبلغ 12 ريال", 1_200_000 + 3_600_000))
    }

    @Test fun decimals() {
        assertEquals(2, PaymentParser.decimalsOf("SAR"))
        assertEquals(0, PaymentParser.decimalsOf("JPY"))
        assertEquals(3, PaymentParser.decimalsOf("BHD"))
    }
}
