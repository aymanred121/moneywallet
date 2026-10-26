package com.oriondev.moneywallet.next.capture

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.oriondev.moneywallet.next.WalletApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reads payment notifications from Google Wallet and the chosen bank / wallet apps into the
 * capture inbox. Nothing leaves the device; notifications from other apps are ignored unread.
 * Every failure is swallowed: a crashing listener gets unbound by the system.
 */
class PaymentNotificationListener : NotificationListenerService() {
    private var syncJob: Job? = null

    override fun onListenerConnected() {
        connected.value = true
        // catch up on what arrived while we were not bound (updates, OEM restrictions) and is
        // still in the shade; the dedupe key keeps anything already captured from doubling
        runCatching { activeNotifications?.forEach(::handle) }
        runCatching {
            val container = WalletApp.container(this)
            syncJob?.cancel()
            syncJob = container.scope.launch {
                container.db.captures().observePending().map { list -> list.mapTo(HashSet()) { it.id } }.distinctUntilChanged()
                    .collect { runCatching { CaptureRecorder.cancelResolved(this@PaymentNotificationListener, it) } }
            }
        }
    }

    override fun onListenerDisconnected() {
        connected.value = false
        syncJob?.cancel()
        syncJob = null
        // the system unbinds us on updates and under memory pressure: ask to be bound again
        runCatching { requestRebind(ComponentName(this, PaymentNotificationListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = handle(sbn)

    private fun handle(sbn: StatusBarNotification?) {
        try {
            val n = sbn?.notification ?: return
            if (sbn.packageName == packageName || sbn.isOngoing) return
            if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
            val items = items(n, sbn.postTime)
            if (items.isEmpty()) return
            val container = WalletApp.container(this)
            val app = applicationContext
            container.scope.launch {
                runCatching {
                    val settings = container.prefs.settings.first()
                    if (!settings.captureEnabled) return@runCatching
                    val sms = settings.captureSms && sbn.packageName in CaptureApps.smsPackages(app)
                    if (!sms && sbn.packageName !in CaptureApps.allowed(settings.captureApps)) return@runCatching
                    // one notification can carry several messages (an SMS thread): each is its own payment
                    for (item in items) CaptureRecorder.record(app, sbn.packageName, item.title, item.text, item.whenMillis, item.whenMillis, strict = sms)
                }
            }
        } catch (e: Throwable) {
            // never let one odd notification take the listener down
        }
    }

    private data class Item(val title: String, val text: String, val whenMillis: Long)

    /**
     * The messages a notification carries. Messaging apps (SMS) use MessagingStyle: one entry per
     * message, and EXTRA_TEXT is only the latest or a "3 new messages" summary, so each message is
     * read on its own with its own time. Re-posts of an old thread are dropped by the dedupe key.
     */
    private fun items(n: Notification, postTime: Long): List<Item> {
        val extras = n.extras ?: return emptyList()
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        if (style != null && style.messages.isNotEmpty()) {
            val thread = style.conversationTitle?.toString()?.takeIf { it.isNotBlank() }
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val recent = postTime - MESSAGE_MAX_AGE
            return style.messages.filter { it.timestamp == 0L || it.timestamp >= recent }.mapNotNull { m ->
                val text = m.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) null
                else Item((m.person?.name?.toString()?.takeIf { it.isNotBlank() } ?: thread).trim(), text, m.timestamp.takeIf { it > 0 } ?: postTime)
            }
        }
        val (title, text) = content(extras)
        if (text.isBlank() && title.isBlank()) return emptyList()
        return listOf(Item(title, text, n.`when`.takeIf { it > 0 } ?: postTime))
    }

    private fun content(extras: Bundle): Pair<String, String> {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        // the big text usually repeats the short text in full; keep the longer one. Inbox lines are
        // summaries of other items, read only when there is nothing else (joining them mixed up
        // several payments into one)
        val main = if (big.length >= text.length) big else text
        return title.trim() to main.ifBlank { lines }.trim()
    }

    companion object {
        /** Older messages still shown in a thread were either captured already or predate capture. */
        private const val MESSAGE_MAX_AGE = 24 * 60 * 60_000L

        /** Whether the system has this listener bound right now (in this process). */
        val connected = kotlinx.coroutines.flow.MutableStateFlow(false)

        /**
         * Access can be granted while the listener is not running: an app update unbinds it, and
         * some OEMs (Xiaomi Autostart, battery savers) refuse to start the process to rebind it.
         * Asks the system to bind again; harmless when already bound or access is off.
         */
        fun ensureBound(context: android.content.Context) {
            if (connected.value || !CaptureApps.hasAccess(context)) return
            val component = ComponentName(context, PaymentNotificationListener::class.java)
            runCatching { requestRebind(component) }
            // requestRebind only follows an earlier requestUnbind; when the system never bound us
            // (update, Autostart block), flipping our own component off and on makes it rebind
            runCatching {
                val pm = context.packageManager
                val flags = android.content.pm.PackageManager.DONT_KILL_APP
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, flags)
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, flags)
            }
        }
    }
}
