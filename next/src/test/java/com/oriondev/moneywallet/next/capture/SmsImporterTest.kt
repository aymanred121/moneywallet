package com.oriondev.moneywallet.next.capture

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.data.db.CaptureStatus
import com.oriondev.moneywallet.next.data.db.CurrencyEntity
import com.oriondev.moneywallet.next.data.db.Direction
import com.oriondev.moneywallet.next.data.db.TransactionEntity
import com.oriondev.moneywallet.next.data.db.WalletEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = WalletApp::class, sdk = [34])
class SmsImporterTest {
    private val now = System.currentTimeMillis()

    /** A fake SMS inbox: salary credit, a purchase already entered by hand, a chat, an OTP. */
    class FakeSms : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)).apply {
                val t = System.currentTimeMillis() - 2 * 60 * 60 * 1000
                addRow(arrayOf("Bank", "Credit Transfer Local\nVia:IPS\nAmount:SR 12345.67\nTo:0000\nFrom: ACME CO.\nFrom:1234", t))
                addRow(arrayOf("Bank", "شراء عبر نقاط البيع\nبطاقة: مدى **1234\nمبلغ: 25.00 ريال\nلدى: مطعم البرجر", t + 1000))
                addRow(arrayOf("Omar", "can you pay me 50 SAR tomorrow?", t + 2000))
                addRow(arrayOf("Bank", "رمز التحقق 123456 لا تشاركه مع أحد", t + 3000))
            }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    @Test fun queuesOnlyNewBankPayments() = runBlocking {
        Robolectric.setupContentProvider(FakeSms::class.java, "sms")
        val app = ApplicationProvider.getApplicationContext<WalletApp>()
        val db = app.container.db
        db.currencies().upsertAll(listOf(CurrencyEntity("SAR", "Saudi Riyal", "SAR", 2)))
        val wallet = db.wallets().insert(WalletEntity(uuid = UUID.randomUUID().toString(), name = "Main", icon = "", currency = "SAR"))
        // the burger purchase was already entered by hand
        db.transactions().insert(TransactionEntity(uuid = UUID.randomUUID().toString(), money = 2500, date = now - 60 * 60 * 1000, direction = Direction.EXPENSE, walletId = wallet))

        val report = SmsImporter.import(app, days = 7)
        assertEquals(4, report.scanned)
        assertEquals(2, report.payments)
        assertEquals(1, report.alreadyRecorded)
        assertEquals(1, report.added)

        val pending = db.captures().observePending().first()
        assertEquals(1, pending.size)
        assertEquals(1234567L, pending.single().amount)
        assertEquals(Direction.INCOME, pending.single().direction)
        assertEquals(CaptureStatus.PENDING, pending.single().status)

        // importing again adds nothing
        assertEquals(0, SmsImporter.import(app, days = 7).added)
    }
}
