package com.oriondev.moneywallet.next.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oriondev.moneywallet.next.WalletApp
import com.oriondev.moneywallet.next.data.db.CaptureStatus
import kotlinx.coroutines.launch

/** "Dismiss" on a capture notification. Not exported: only our own PendingIntents reach it. */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        CaptureRecorder.cancel(context, id)
        val pending = goAsync()
        WalletApp.container(context).scope.launch {
            try {
                val dao = WalletApp.container(context).db.captures()
                if (dao.byId(id)?.status == CaptureStatus.PENDING) dao.setStatus(id, CaptureStatus.DISMISSED)
            } catch (e: Exception) {
                // the capture stays in the inbox; nothing else to do
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.oriondev.moneywallet.next.capture.DISMISS"
        const val EXTRA_ID = "captureId"
    }
}
