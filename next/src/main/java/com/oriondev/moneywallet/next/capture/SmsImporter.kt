package com.oriondev.moneywallet.next.capture

import android.content.Context
import android.provider.Telephony
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.core.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Reads the SMS inbox for a recent window and queues bank payments in the capture inbox, for
 * messages that arrived before capture was running. Uses the same strict SMS parser and learned
 * rules as live capture; other messages are read and dropped, never stored. Payments already
 * recorded as a transaction (same amount and direction that day) are skipped.
 */
object SmsImporter {
    data class Report(val scanned: Int, val payments: Int, val added: Int, val alreadyRecorded: Int, val alreadyCaptured: Int)

    suspend fun import(context: Context, days: Int): Report = withContext(Dispatchers.IO) {
        val container = WalletApp.container(context)
        val settings = container.prefs.settings.first()
        val rules = settings.captureRules.mapNotNull(HeaderRule::decode)
        // same package as live SMS capture, so an imported message and its notification dedupe
        val smsPackage = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull() ?: "com.google.android.apps.messaging"
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        var scanned = 0; var payments = 0; var added = 0; var recorded = 0; var captured = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        ) ?: return@withContext Report(0, 0, 0, 0, 0)
        cursor.use { c ->
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(0).orEmpty()
                val body = c.getString(1).orEmpty()
                val date = c.getLong(2)
                val payment = PaymentParser.parse(sender, body, settings.baseCurrency ?: "SAR", strict = true, rules = rules) as? ParseResult.Payment ?: continue
                payments++
                val day = Dates.toLocalDate(date)
                val entered = container.db.captures().countMatchingTransactions(
                    payment.amountMinor, payment.currency, payment.direction, Dates.startOfDay(day.minusDays(1)), Dates.startOfDay(day.plusDays(2)),
                ) > 0
                if (entered) { recorded++; continue }
                val id = CaptureRecorder.record(context, smsPackage, sender, body, date, date, strict = true, notify = false)
                if (id != null) added++ else captured++
            }
        }
        Report(scanned, payments, added, recorded, captured)
    }
}
