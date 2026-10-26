package com.oriondev.moneywallet.next.capture

import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.db.Direction
import java.security.MessageDigest

/** What [PaymentParser] made of a notification. */
sealed interface ParseResult {
    /**
     * [amountMinor] is in minor units of [currency] (ISO code); [direction] is a [Direction] value;
     * [keyword] is true when payment wording (purchase, credited, شراء, واردة...) backed the direction.
     */
    data class Payment(
        val amountMinor: Long, val currency: String, val merchant: String?, val direction: Int, val confidence: Float, val keyword: Boolean = true,
        /** The header phrase that decided credit vs debit ("credit transfer", "شراء"), when a bank format was recognised. */
        val matchedType: String? = null,
        /** True when a rule the user taught decided the direction. */
        val learned: Boolean = false,
    ) : ParseResult
    data class Ignored(val reason: IgnoreReason) : ParseResult
}

enum class IgnoreReason(val label: String) {
    OTP("a one-time or verification code"),
    DECLINED("a declined or failed payment"),
    PROMOTION("a promotion"),
    NO_AMOUNT("no payment amount found"),
    NOT_PAYMENT("no payment wording (SMS needs words like purchase, credited, شراء or واردة)"),
}

/**
 * Reads payment notifications (Google Wallet, bank and wallet apps, bank SMS, in English and
 * Arabic) into amount, currency, merchant and direction. Pure Kotlin so it is fully unit-tested.
 *
 * Amounts need a currency next to them ("SAR 18.50", "45.00 ريال", "⃁ 12") or an amount label
 * ("مبلغ: 45"); amounts after a balance / limit label are never taken for the payment.
 */
object PaymentParser {

    /**
     * [strict] is for SMS, where personal messages can mention money ("send me 50 SAR"): only
     * texts with payment wording and an explicit currency or amount label count.
     */
    fun parse(title: String, text: String, fallbackCurrency: String = "SAR", strict: Boolean = false, rules: List<HeaderRule> = emptyList()): ParseResult {
        val head = normalize(title).trim()
        val body = normalize(text).trim()
        val all = if (head.isEmpty()) body else if (body.isEmpty()) head else "$head\n$body"
        val lower = all.lowercase()
        when {
            OTP.containsMatchIn(lower) -> return ParseResult.Ignored(IgnoreReason.OTP)
            DECLINED.containsMatchIn(lower) -> return ParseResult.Ignored(IgnoreReason.DECLINED)
            PROMO.containsMatchIn(lower) && !CONFIRMED.containsMatchIn(lower) -> return ParseResult.Ignored(IgnoreReason.PROMOTION)
        }
        // bank format first: a known transaction-type header + "Key: Value" fields
        structured(head, body, all, fallbackCurrency, strict, rules)?.let { return it }
        val amount = findAmount(all, fallbackCurrency) ?: return ParseResult.Ignored(IgnoreReason.NO_AMOUNT)
        val (direction, sure) = direction(normalize(title).lowercase(), normalize(text).lowercase())
        if (strict && (!sure || !(amount.explicitCurrency || amount.labelled) || !BANK_CONTEXT.containsMatchIn(lower))) return ParseResult.Ignored(IgnoreReason.NOT_PAYMENT)
        val merchant = merchant(all, direction) ?: titleMerchant(head)
        var confidence = 0.4f
        if (amount.explicitCurrency) confidence += 0.25f
        if (amount.labelled) confidence += 0.05f
        if (sure) confidence += 0.15f
        if (merchant != null) confidence += 0.15f
        return ParseResult.Payment(amount.minor, amount.iso, merchant, direction, confidence.coerceAtMost(1f), keyword = sure)
    }

    /**
     * Reads the usual bank layout: the header (title, first body line or a "Type:" field) names
     * the transaction type, which decides credit vs debit; the Amount field gives the amount and
     * From (credit) or At / To (debit) the counterparty. Null when no type header is recognised,
     * so the keyword heuristics below take over. For SMS a bank-style field (or a card / account
     * mention) is also required, so a chat saying "payment for dinner 50 SAR" is not a payment.
     */
    private fun structured(head: String, body: String, all: String, fallbackCurrency: String, strict: Boolean, rules: List<HeaderRule>): ParseResult.Payment? {
        val fields = BankSms.fields(body) + BankSms.fields(head)
        val firstBodyLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val headers = listOf(head, firstBodyLine) + fields.filter { it.first == BankSms.Field.TYPE }.map { it.second }
        val type = BankSms.classify(headers, rules) ?: return null
        val bankish = fields.any { it.first != BankSms.Field.DATE && it.first != BankSms.Field.TYPE } || BANK_CONTEXT.containsMatchIn(all.lowercase())
        if (strict && !bankish && !type.custom) return null
        val amountValue = fields.firstOrNull { it.first == BankSms.Field.AMOUNT }?.second
        val amount = amountValue?.let { v ->
            findAmount(v, fallbackCurrency) ?: BARE_NUMBER.find(v)?.let { m -> toMinor(m.groupValues[1], fallbackCurrency.uppercase())?.let { Found(it, fallbackCurrency.uppercase(), false, true) } }
        } ?: findAmount(all, fallbackCurrency) ?: return null
        fun names(vararg f: BankSms.Field) = fields.filter { it.first in f }.map { it.second }.filter(BankSms::isName).firstNotNullOfOrNull { clean(cut(it)) }
        val merchant = if (type.direction == Direction.INCOME) names(BankSms.Field.FROM) else names(BankSms.Field.AT) ?: names(BankSms.Field.TO)
        return ParseResult.Payment(
            amount.minor, amount.iso, merchant ?: merchant(all, type.direction), type.direction,
            confidence = 0.8f + (if (amount.explicitCurrency) 0.1f else 0f) + (if (amountValue != null) 0.05f else 0f) + (if (merchant != null) 0.05f else 0f),
            keyword = true, matchedType = type.label, learned = type.custom,
        )
    }

    private val BARE_NUMBER = Regex("^\\s*(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,3})?|\\d+(?:[.,]\\d{1,3})?)\\s*$")

    /** Minor-unit digits of an ISO currency (ISO 4217); 2 unless known otherwise. */
    fun decimalsOf(iso: String): Int = when (iso) {
        "JPY", "KRW", "VND", "CLP", "ISK", "UGX", "XAF", "XOF", "PYG", "RWF" -> 0
        "KWD", "BHD", "OMR", "JOD", "TND", "LYD", "IQD" -> 3
        else -> 2
    }

    /**
     * Identity of a notification for de-duplication: same app, same text (whitespace, case and
     * digit script ignored) and same minute of the notification's own timestamp.
     */
    fun dedupeKey(packageName: String, title: String, text: String, whenMillis: Long): String {
        val norm = normalize("$title\n$text").lowercase().replace(WS, " ").trim()
        val bytes = MessageDigest.getInstance("SHA-256").digest("$packageName|$norm|${whenMillis / 60_000}".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Arabic-Indic digits and separators to ASCII, invisible bidi marks removed, odd spaces to plain ones. */
    internal fun normalize(s: String): String = buildString(s.length) {
        for (ch in s) when (ch) {
            in '٠'..'٩' -> append('0' + (ch - '٠'))
            in '۰'..'۹' -> append('0' + (ch - '۰'))
            '٫' -> append('.') // Arabic decimal separator
            '٬', '،' -> append(',') // Arabic thousands separator, Arabic comma
            '٪' -> append('%')
            '\u00A0', '\u2007', '\u202F', '\u2009' -> append(' ')
            '\u200B', '\u200C', '\u200D', '\u200E', '\u200F', '\u061C', in '\u202A'..'\u202E', in '\u2066'..'\u2069' -> Unit
            else -> append(ch)
        }
    }

    // ---------------- amount ----------------

    private class Found(val minor: Long, val iso: String, val explicitCurrency: Boolean, val labelled: Boolean)
    private class Candidate(val start: Int, val end: Int, val iso: String, val number: String, val currencyFirst: Boolean)

    private fun findAmount(all: String, fallbackCurrency: String): Found? {
        val candidates = candidates(all)
        var best: Candidate? = null
        var bestScore = Int.MIN_VALUE
        var bestLabelled = false
        var prevEnd = 0
        for (c in candidates) {
            val lineStart = all.lastIndexOf('\n', c.start - 1) + 1
            val context = all.substring(maxOf(lineStart, prevEnd).coerceAtMost(c.start), c.start)
            prevEnd = c.end
            if (BALANCE.containsMatchIn(context)) continue
            val labelled = AMOUNT_LABEL.containsMatchIn(context)
            val score = (if (labelled) 2 else 0) - (if (FEE.containsMatchIn(context)) 3 else 0)
            if (score > bestScore) { best = c; bestScore = score; bestLabelled = labelled }
        }
        best?.let { c -> toMinor(c.number, c.iso)?.let { return Found(it, c.iso, true, bestLabelled) } }
        if (candidates.isNotEmpty()) return null // only balances (or unreadable numbers): not a payment
        val labelled = LABELLED_NUMBER.find(all) ?: return null
        val context = all.substring(all.lastIndexOf('\n', labelled.range.first) + 1, labelled.range.first)
        if (BALANCE.containsMatchIn(context)) return null
        val iso = fallbackCurrency.uppercase()
        return toMinor(labelled.groupValues[1], iso)?.let { Found(it, iso, false, true) }
    }

    /** Currency + number pairs in either order; where two overlap ("•• 12 SAR 5.00") the one with decimals, else currency-first, wins. */
    private fun candidates(all: String): List<Candidate> {
        val raw = CURRENCY_NUMBER.findAll(all).map { Candidate(it.range.first, it.range.last + 1, isoOf(it.groupValues[1]), it.groupValues[2], true) } +
            NUMBER_CURRENCY.findAll(all).map { Candidate(it.range.first, it.range.last + 1, isoOf(it.groupValues[2]), it.groupValues[1], false) }
        val kept = ArrayList<Candidate>()
        for (c in raw.sortedBy { it.start }) {
            val last = kept.lastOrNull()
            if (last == null || c.start >= last.end) { kept += c; continue }
            fun rank(x: Candidate) = (if (x.number.contains('.') || x.number.contains(',')) 2 else 0) + (if (x.currencyFirst) 1 else 0)
            if (rank(c) > rank(last)) kept[kept.lastIndex] = c
        }
        return kept
    }

    private fun toMinor(number: String, iso: String): Long? =
        MoneyFormat.parse(number, Currency(iso, iso, iso, decimalsOf(iso)))?.takeIf { it > 0 }

    private fun isoOf(token: String): String = CURRENCIES.first { it.regex.matches(token) }.iso

    // ---------------- direction ----------------

    /**
     * Direction and whether a keyword backed it (otherwise it defaults to expense). Bank alerts
     * lead with the transaction type ("Purchase", "Credited", "حوالة واردة", "شراء"), and later
     * lines often carry words of the other kind ("received by", "from your account", "Available
     * credit"), so the earliest keyword wins, the title before the body. A refund or reversal is
     * income wherever it appears ("Purchase refund").
     */
    private fun direction(title: String, body: String): Pair<Int, Boolean> {
        val all = "$title\n$body"
        if (REFUND.containsMatchIn(all)) return Direction.INCOME to true
        for (part in listOf(title, body)) {
            val income = STRONG_INCOME.find(part)?.range?.first
            val expense = EXPENSE.find(part)?.range?.first
            when {
                income != null && (expense == null || income < expense) -> return Direction.INCOME to true
                expense != null -> return Direction.EXPENSE to true
            }
        }
        return if (WEAK_INCOME.containsMatchIn(all)) Direction.INCOME to true else Direction.EXPENSE to false
    }

    // ---------------- merchant ----------------

    private fun merchant(all: String, direction: Int): String? {
        val patterns = buildList {
            add(AR_MERCHANT); add(EN_MERCHANT)
            add(if (direction == Direction.INCOME) AR_FROM else AR_TO)
            add(if (direction == Direction.INCOME) AR_TO else AR_FROM)
            add(EN_TO_FROM)
        }
        for (p in patterns) for (m in p.findAll(all)) clean(cut(m.groupValues[1]))?.let { return it }
        return null
    }

    /** Google Wallet puts the merchant alone in the title ("Starbucks" / "SAR 18.50 with Visa ••1234"). */
    private fun titleMerchant(title: String): String? {
        if (title.isEmpty() || title.contains('\n') || title.any { it.isDigit() }) return null
        if (GENERIC_TITLE.containsMatchIn(title.lowercase())) return null
        return clean(title)
    }

    /** Merchant text ends before a date, time, amount, card / balance words or punctuation. */
    private fun cut(value: String): String {
        var end = value.length
        MERCHANT_STOP.find(value)?.let { end = minOf(end, it.range.first) }
        CURRENCY_NUMBER.find(value)?.let { end = minOf(end, it.range.first) }
        NUMBER_CURRENCY.find(value)?.let { end = minOf(end, it.range.first) }
        return value.substring(0, end)
    }

    private fun clean(raw: String): String? {
        var s = raw.replace(WS, " ").trim().trim(':', '-', '–', '.', ',', '*', '"', '\'', ' ')
        s = s.replace(COUNTRY_SUFFIX, "").trim()
        if (s.length > 48) s = s.take(48).trimEnd()
        val letters = s.count { it.isLetter() }
        val digits = s.count { it.isDigit() }
        if (letters < 2 || digits >= letters || BAD_MERCHANT_START.containsMatchIn(s)) return null
        if (s.any { it in 'A'..'Z' } && s.none { it in 'a'..'z' }) {
            // ALL CAPS from the bank: title-case words, but keep short vowel-less acronyms (STC, BSF, H&M)
            s = s.split(' ').joinToString(" ") { w ->
                if (w.length <= 3 && w.none { it in "AEIOU" }) w else w.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        }
        return s
    }

    // ---------------- vocabulary ----------------

    private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
    private const val AR = "\\u0600-\\u06FF"
    private fun latin(code: String) = "(?<![A-Za-z])$code(?![A-Za-z])"

    private class Cur(val iso: String, val pattern: String) { val regex = Regex(pattern, RegexOption.IGNORE_CASE) }

    /** Order matters where tokens share a prefix: "ريال عماني" before "ريال", "E£" handled at its own start. */
    private val CURRENCIES = listOf(
        Cur("OMR", "ريال عماني|ر\\.\\s?ع\\.?|${latin("OMR")}"),
        Cur("QAR", "ريال قطري|ر\\.\\s?ق\\.?|${latin("QAR")}"),
        Cur("SAR", "ريال سعودي|ريالات|ريالا|ريال|ر\\.\\s?س\\.?|${latin("SAR")}|${latin("SR")}|\u20C1"),
        Cur("USD", "US\\$|${latin("USD")}|\\$|دولار"),
        Cur("EUR", "${latin("EUR")}|€|يورو"),
        Cur("AED", "${latin("AED")}|د\\.\\s?إ\\.?|درهم"),
        Cur("EGP", "${latin("EGP")}|E£|جنيه مصري|ج\\.\\s?م\\.?"),
        Cur("GBP", "${latin("GBP")}|£|جنيه [إا]سترليني"),
        Cur("EGP", "جنيه"),
        Cur("KWD", "${latin("KWD")}|دينار كويتي|د\\.\\s?ك\\.?"),
        Cur("BHD", "${latin("BHD")}|دينار بحريني|د\\.\\s?ب\\.?"),
        Cur("JOD", "${latin("JOD")}|دينار [أا]ردني"),
        Cur("JPY", "${latin("JPY")}|¥"),
        Cur("INR", "${latin("INR")}|₹"),
        Cur("TRY", "${latin("TRY")}|₺"),
    )
    private val CURRENCY = CURRENCIES.joinToString("|") { "(?:${it.pattern})" }

    /** 1,250.00 | 1.250,00 | 1250,5 | 18.50 | 45 — never right after a card mask or another digit. */
    private const val NUMBER = "(?<![\\d.,•*#·])(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,3})?|\\d{1,3}(?:\\.\\d{3})+,\\d{1,2}|\\d+(?:[.,]\\d{1,3})?)(?!\\d)"
    private val CURRENCY_NUMBER = rx("($CURRENCY)[ \\t]*:?[ \\t]*$NUMBER")
    private val NUMBER_CURRENCY = rx("$NUMBER[ \\t]*($CURRENCY)")
    private val LABELLED_NUMBER = rx("(?:amount|\\bamt\\b|مبلغ|بقيمة)[ \\t]*[:：]?[ \\t]*$NUMBER")

    private val WS = Regex("\\s+")
    private val BALANCE = rx("balance|\\bbal\\b|avail|remaining|limit|الرصيد|رصيد|المتاح|المتبقي|الحد")
    private val FEE = rx("\\bfees?\\b|\\bvat\\b|رسوم|ضريبة")
    private val AMOUNT_LABEL = rx("amount|\\bamt\\b|total|\\bpaid\\b|purchase|spent|charged|received|refund|مبلغ|قيمة|شراء|سحب|إيداع|ايداع|استرداد|الإجمالي")

    private val OTP = rx(
        "\\botp\\b|one[- ]time (?:password|passcode|code|pin)|verification code|\\bverify\\b|passcode|security code|activation code|login code|" +
            "do not share|don'?t share|رمز التحقق|رمز التفعيل|رمز الدخول|رمز التأكيد|رمز التاكيد|رمز لمرة واحدة|كلمة المرور|كلمة السر|الرمز السري|رمز سري|لا تشارك",
    )
    private val DECLINED = rx("declined|was not (?:completed|successful)|unsuccessful|\\bfailed\\b|insufficient|رفض|مرفوض|لم تتم|لم تنجح|فشل|غير ناجح|غير كاف")
    private val PROMO = rx(
        "\\boffers?\\b|\\bpromo|promotion|discount|\\d+ ?% ?off\\b|limited time|\\bwin\\b|\\bdeals?\\b|" +
            "(?<![$AR])عرض|(?<![$AR])عروض|تخفيض|لفترة محدودة|احصل على|استمتع|اربح|خصم \\d+ ?%|\\d+ ?% ?خصم",
    )
    /** Wording of a completed transaction; a promo-sounding message with one of these is still a payment. */
    private val CONFIRMED = rx(
        "purchase (?:of|at|from|transaction|alert|approved)|\\bpaid\\b|debited|credited|received|refunded|withdrawn|\\bspent\\b|" +
            "تم خصم|خصم مبلغ|تم شراء|عملية شراء|شراء عبر|تم إيداع|تم ايداع|تم استرداد|حوالة واردة|حوالة صادرة|تم سحب|تم الدفع",
    )

    /** What a bank alert mentions and a chat message rarely does: a card / account (masked), balance, mada, IBAN. */
    private val BANK_CONTEXT = rx(
        "[*x•#]{2,}\\s?\\d{2,4}|\\bcard\\b|account|\\bacct\\b|a/c|\\bmada\\b|visa|master ?card|apple ?pay|balance|\\biban\\b|\\bpos\\b|" +
            "بطاقة|بطاقه|حساب|مدى|رصيد|الرصيد|فيزا|ماستر|آيبان|ايبان|نقاط البيع|أبل باي|ابل باي",
    )
    private val REFUND = rx("refund|reversal|reversed|charge ?back|استرداد|مسترد|مسترجع|عكس عملية|إلغاء عملية|الغاء عملية")
    private val STRONG_INCOME = rx(
        "refund|credited|\\bcredit (?:of|to|alert)|received|\\breceive\\b|deposit|reversal|reversed|incoming|salary|money in|" +
            "إيداع|ايداع|أودع|اودع|استرداد|مسترد|حوالة واردة|تحويل وارد|واردة|استلام|استلمت|راتب|دائن",
    )
    private val EXPENSE = rx(
        "purchase|\\bpaid\\b|\\bpay\\b|payment|debited|outgoing|transfer out|money out|\\bdebit\\b(?! card)|spent|withdraw|charged|\\bsent\\b|transfer(?:red)? to|\\bpos\\b|" +
            "شراء|خصم|سحب|دفع|مدفوعات|سداد|حوالة صادرة|تحويل صادر|صادرة|مدين",
    )
    private val WEAK_INCOME = rx("cashback|cash back|إضافة|اضافة|أضيف|اضيف|وارد")

    private const val VALUE = "([^\\n]+)"
    private val AR_MERCHANT = rx("(?:لدى|اسم التاجر|التاجر|المتجر|(?<![$AR])عند)[ \\t]*[:：]?[ \\t]*$VALUE")
    private val AR_TO = rx("(?:للمستفيد|المستفيد|(?<![$AR])إلى|(?<![$AR])الى)[ \\t]*[:：]?[ \\t]*$VALUE")
    private val AR_FROM = rx("(?:المرسل|(?<![$AR])من)[ \\t]*[:：]?[ \\t]*$VALUE")
    private val EN_MERCHANT = rx("(?:\\bat\\b|\\bmerchant\\b|\\bpayee\\b|@)[ \\t]*:?[ \\t]*$VALUE")
    private val EN_TO_FROM = rx("\\b(?:to|from)\\b[ \\t]*:?[ \\t]*$VALUE")
    private val MERCHANT_STOP = rx(
        "[ \\t]+(?:on|with|has|have|was|were|is|been|and|using|via|for|by|card|ref|reference|avail\\w*|balance|bal|في|بتاريخ|بمبلغ|مبلغ|بقيمة|عبر|بواسطة|باستخدام|رصيد|الرصيد|المتاح|تاريخ|تم)(?=[\\s:.,]|$)|" +
            "[;,|]|\\.(?:\\s|$)|[ \\t]+\\d{1,4}[/.-]\\d{1,2}|[ \\t]*\\d{1,2}:\\d{2}",
    )
    private val COUNTRY_SUFFIX = rx("\\s+(?:KSA|SAU|SA|UAE|AE|USA|US|UK|GB)$")
    private val BAD_MERCHANT_START = rx("^(?:your|you|my|the (?:card|account)|account|acct|card|a/c|wallet|حساب|حسابك|بطاقة|بطاقتك|خلال|رصيد|الرصيد|المحفظة|محفظتك)")
    private val GENERIC_TITLE = rx(
        "wallet|bank|alert|transaction|purchase|payment|notification|\\bcard\\b|mada|visa|mastercard|debit|credit|refund|received|\\bsent\\b|\\bpaid\\b|" +
             "شراء|عملية|تنبيه|بنك|مصرف|حوالة|إشعار|اشعار|بطاقة|مدى|دفع|سحب|إيداع|ايداع|استرداد|محفظة",
    )
}
