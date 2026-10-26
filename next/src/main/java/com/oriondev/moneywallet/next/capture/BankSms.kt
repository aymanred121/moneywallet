package com.oriondev.moneywallet.next.capture

import com.oriondev.moneywallet.next.data.db.Direction

/**
 * A message type the user taught ("Credit Transfer Local" is income). Matched before the built-in
 * dictionary, as a case-insensitive phrase in the message's header line. Stored as "I|phrase" /
 * "E|phrase" in settings.
 */
data class HeaderRule(val phrase: String, val direction: Int) {
    fun encode(): String = (if (direction == Direction.INCOME) "I|" else "E|") + phrase

    companion object {
        fun decode(raw: String): HeaderRule? {
            val dir = when (raw.substringBefore('|', "")) { "I" -> Direction.INCOME; "E" -> Direction.EXPENSE; else -> return null }
            val phrase = normalizePhrase(raw.substringAfter('|'))
            return if (phrase.length >= 3) HeaderRule(phrase, dir) else null
        }

        fun normalizePhrase(s: String): String = PaymentParser.normalize(s).lowercase().replace(Regex("\\s+"), " ").trim().take(60)
    }
}

/**
 * Structure of typical bank SMS and app alerts: a transaction-type header on the
 * first line ("Credit Transfer Local", "POS Purchase", "شراء عبر نقاط البيع", "حوالة واردة"),
 * then "Key: Value" lines (Amount/مبلغ, From/من, To/إلى, At/لدى, Via/عبر, Card/بطاقة...).
 * The header decides credit vs debit; the fields give amount and counterparty.
 */
internal object BankSms {

    enum class Field { AMOUNT, FROM, TO, AT, VIA, CARD, ACCOUNT, BALANCE, FEE, DATE, TYPE, REFERENCE }

    /** What a type header says, and the phrase that said it (shown in the test result). */
    data class Classified(val direction: Int, val label: String, val custom: Boolean)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private const val AR = "\\u0600-\\u06FF"
    /** Arabic words have no \b: a phrase must not continue an Arabic word on either side. */
    private fun ar(p: String) = "(?<![$AR])(?:$p)(?![$AR])"

    /** Refunds and reversals are income even when the header also says "purchase". */
    private val REFUND = rx(
        "\\brefund(?:ed)?\\b|\\breversal\\b|\\breversed\\b|\\bcharge ?back\\b|" +
            ar("استرداد|مسترد|مسترجع|عكس عملية|إلغاء عملية|الغاء عملية|عكس قيد"),
    )

    private val INCOME = listOf(
        "\\bcredit\\s*transfer\\b", "\\bincoming\\b", "\\btransfer\\s*(?:received|in)\\b", "\\breceived\\s*(?:a\\s*)?(?:local\\s*)?transfer\\b",
        "\\b(?:cash\\s*)?deposit(?:ed)?\\b", "\\bsalary\\b", "\\bpayroll\\b", "\\bcash\\s*back\\b|\\bcashback\\b",
        "\\bcredit\\s*(?:alert|advice|notification|note)\\b", "^\\s*credit\\b(?!\\s*card)", "\\bcredited\\b", "\\b(?:payment|money)\\s*received\\b",
        "\\bpay\\s*in\\b", "\\bwallet\\s*top[- ]?up\\b|\\btop[- ]?up\\s*received\\b",
        ar("حوالة(?:\\s+\\S+){0,2}\\s+واردة|تحويل(?:\\s+\\S+){0,2}\\s+وارد|حوالة مستلمة|استلام حوالة|وارد"),
        ar("إيداع|ايداع|أودع|اودع|راتب|رواتب|إضافة رصيد|اضافة رصيد|دائن|قيد دائن|استلام مبلغ"),
    ).map(::rx)

    private val EXPENSE = listOf(
        "\\bdebit\\s*transfer\\b", "\\boutgoing\\b", "\\btransfer\\s*(?:sent|out)\\b", "\\bsent\\s*(?:a\\s*)?transfer\\b",
        "\\bpurchase\\b", "\\bpos\\b", "\\bwithdrawal\\b|\\bwithdrawn\\b", "\\bbill\\s*payment\\b|\\bsadad\\b", "\\bpayment\\b", "\\bpaid\\b",
        "\\bdebit\\s*(?:alert|advice|notification|note)\\b", "^\\s*debit\\b(?!\\s*card)", "\\bdebited\\b", "\\bcharged?\\b", "\\bsubscription\\b",
        "\\bapple\\s*pay\\b", "\\bonline\\s*shopping\\b",
        ar("شراء|مشتريات|حوالة(?:\\s+\\S+){0,2}\\s+صادرة|تحويل(?:\\s+\\S+){0,2}\\s+صادر|صادرة|سحب|سداد|دفع|مدفوعات|خصم|مدين|قيد مدين|رسوم"),
    ).map(::rx)

    /**
     * Direction from the header lines (the title, the first body line, a "Type:" field), in that
     * order. User rules first, then refunds, then whichever built-in type appears earliest.
     */
    fun classify(headers: List<String>, rules: List<HeaderRule>): Classified? {
        for (raw in headers) {
            val line = HeaderRule.normalizePhrase(raw)
            if (line.isEmpty()) continue
            rules.firstOrNull { line.contains(it.phrase) }?.let { return Classified(it.direction, it.phrase, custom = true) }
            REFUND.find(line)?.let { return Classified(Direction.INCOME, it.value, custom = false) }
            val income = INCOME.mapNotNull { it.find(line) }.minByOrNull { it.range.first }
            val expense = EXPENSE.mapNotNull { it.find(line) }.minByOrNull { it.range.first }
            val hit = listOfNotNull(income?.let { it to Direction.INCOME }, expense?.let { it to Direction.EXPENSE }).minByOrNull { it.first.range.first }
            if (hit != null) return Classified(hit.second, hit.first.value.trim(), custom = false)
        }
        return null
    }

    private val KEYS: List<Pair<Field, Regex>> = listOf(
        Field.AMOUNT to "amount|amt|value|total|المبلغ|مبلغ|بمبلغ|القيمة|قيمة|الإجمالي",
        Field.BALANCE to "balance|bal|avail\\w*(?: balance)?|remaining|الرصيد(?: المتاح)?|رصيد|المتاح|المتبقي",
        Field.FEE to "fees?|charges?|vat|commission|رسوم|الرسوم|ضريبة|عمولة",
        Field.FROM to "from(?: account| acc)?|sender|by|من(?: حساب)?|المرسل|المحول",
        Field.TO to "to(?: account| acc)?|beneficiary|recipient|payee|إلى|الى|المستفيد|للمستفيد|لـ",
        Field.AT to "at|merchant|store|shop|لدى|التاجر|المتجر|عند|نقطة البيع",
        Field.VIA to "via|through|channel|using|عبر|بواسطة|من خلال",
        Field.CARD to "card(?: no| number)?|بطاقة|البطاقة|مدى",
        Field.ACCOUNT to "account(?: no| number)?|acc|a/c|acct|iban|حساب|الحساب|آيبان|ايبان",
        Field.DATE to "date|time|on|في|التاريخ|تاريخ|الوقت|بتاريخ",
        Field.TYPE to "type|transaction(?: type)?|operation|نوع العملية|العملية|نوع",
        Field.REFERENCE to "ref(?:erence)?(?: no)?|rrn|trx id|الرقم المرجعي|مرجع|رقم العملية",
    ).map { (f, p) -> f to Regex("^(?:$p)$", RegexOption.IGNORE_CASE) }

    private val LINE = Regex("^\\s*([^:：\\n]{1,26}?)\\s*[:：]\\s*(.*?)\\s*$")

    /** "Key: Value" lines in English or Arabic, in order; unknown keys are skipped. */
    fun fields(text: String): List<Pair<Field, String>> = text.lines().mapNotNull { line ->
        val m = LINE.find(line) ?: return@mapNotNull null
        val key = m.groupValues[1].trim().trimEnd('.').lowercase()
        val field = KEYS.firstOrNull { it.second.matches(key) }?.first ?: return@mapNotNull null
        field to m.groupValues[2]
    }

    /** A counterparty name, not an account suffix, card mask or phone number ("From:8001", "To:**0522"). */
    fun isName(value: String): Boolean {
        val letters = value.count { it.isLetter() }
        val digits = value.count { it.isDigit() }
        return letters >= 2 && letters > digits
    }
}
