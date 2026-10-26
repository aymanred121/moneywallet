package com.oriondev.moneywallet.next

import com.oriondev.moneywallet.next.core.Convert
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.Dates
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.core.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    private val usd = Currency("USD", "US Dollar", "$", 2)
    private val sar = Currency("SAR", "Saudi Riyal", "ر.س", 2)
    private val jpy = Currency("JPY", "Yen", "¥", 0)

    @Test fun convertsWithDifferentDecimals() {
        // 150.00 SAR at 1 SAR = 0.266667 USD -> 40.00 USD
        assertEquals(4000L, Convert.minor(sar, usd, 15000, 0.2666667))
        // 10.00 USD at 1 USD = 147.5 JPY -> 1475 JPY (0 decimals)
        assertEquals(1475L, Convert.minor(usd, jpy, 1000, 147.5))
    }

    @Test fun crossRateFromUsdTable() {
        val rates = mapOf("USD" to 1.0, "SAR" to 3.75, "EUR" to 0.9)
        assertEquals(0.2666667, Convert.crossRate(rates, "SAR", "USD")!!, 1e-6)
        assertEquals(3.75 / 0.9, Convert.crossRate(rates, "EUR", "SAR")!!, 1e-9)
        assertNull(Convert.crossRate(rates, "XXX", "USD"))
    }

    @Test fun parsesUserInput() {
        assertEquals(1250075L, MoneyFormat.parse("12,500.75", usd))
        assertEquals(1500L, MoneyFormat.parse("15", usd))
        assertEquals(150L, MoneyFormat.parse("150", jpy))
        assertNull(MoneyFormat.parse("abc", usd))
    }

    @Test fun parsesCommaDecimalsAndArabicDigits() {
        assertEquals(1250L, MoneyFormat.parse("12,50", usd))
        assertEquals(1250L, MoneyFormat.parse("12,5", usd))
        assertEquals(125000L, MoneyFormat.parse("1,250", usd)) // three digits after a lone comma = grouping
        assertEquals(125075L, MoneyFormat.parse("1.250,75", usd))
        assertEquals(1250L, MoneyFormat.parse("١٢٫٥", sar))
        assertEquals(150000L, MoneyFormat.parse("١٬٥٠٠", sar))
    }

    @Test fun conversionIsExactOnLargeAmounts() {
        // 1,234,567.89 SAR at exactly 1 -> no Double drift
        assertEquals(123456789L, Convert.minor(sar, usd, 123456789L, 1.0))
        // 0.125 rounds half-even: 1.00 SAR * 0.125 = 0.125 USD -> 0.12
        assertEquals(12L, Convert.minor(sar, usd, 100L, 0.125))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsBrokenRates() {
        Convert.minor(sar, usd, 100L, Double.POSITIVE_INFINITY)
    }

    @Test fun legacyDatesRoundTrip() {
        val millis = Dates.parseSqlDateTime("2026-09-10 21:50:12")!!
        assertEquals("2026-09-10 21:50:12", Dates.formatSqlDateTime(millis))
        assertEquals("2026-09-10", Dates.formatSqlDate(millis))
    }

    @Test fun legacyIconSpecRoundTrip() {
        val letter = IconSpec.parse("""{"type":"color","color":"#000000","name":"MA"}""")
        assertEquals(IconSpec.Letter("#000000", "MA"), letter)
        val res = IconSpec.parse("""{"type":"resource","resource":"ic_icon_cocktail"}""")
        assertEquals(IconSpec.Resource("ic_icon_cocktail"), res)
        assertEquals(res, IconSpec.parse(res.encode()))
    }
}
