package com.oriondev.moneywallet.next.review

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms do not survive a reboot or an app update: re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ReviewScheduler.ensureScheduled(context)
        }
    }
}
