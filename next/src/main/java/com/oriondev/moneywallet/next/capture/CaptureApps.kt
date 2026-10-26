package com.oriondev.moneywallet.next.capture

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Apps whose notifications are read, and the system switch that lets us read them. */
object CaptureApps {

    /**
     * Built-in list used while the user has not picked apps: the wallet apps whose payment
     * notifications have a known shape. Bank apps are ticked in the chooser; bank SMS are read
     * through the SMS app (see [smsPackages]).
     */
    val defaults: Map<String, String> = linkedMapOf(
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
        "com.samsung.android.spay" to "Samsung Wallet",
    )

    fun allowed(chosen: Set<String>): Set<String> = chosen.ifEmpty { defaults.keys }

    /** Common SMS apps; the phone's default SMS app is added at runtime (OEMs ship their own). */
    private val knownSms = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.miui.smsextra", "com.oneplus.mms", "com.huawei.message", "com.coloros.mms",
    )

    /** Packages treated as bank SMS sources (read with the strict parser) when "Bank SMS" is on. */
    fun smsPackages(context: Context): Set<String> =
        knownSms + listOfNotNull(runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull())

    fun isSms(context: Context, packageName: String) = packageName in smsPackages(context)

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        defaults[packageName] ?: packageName
    }

    data class App(val packageName: String, val label: String)

    /** Launchable apps (visible through the manifest's launcher `<queries>`, no QUERY_ALL_PACKAGES). */
    fun launchable(context: Context): List<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { App(it, label(context, it)) }
            .sortedBy { it.label.lowercase() }
    }

    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) || Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    /**
     * Xiaomi (MIUI / HyperOS) blocks starting apps in the background unless "Autostart" is on, which
     * also stops the system from binding our notification listener. Opens that switch, or the
     * app's settings page when the OEM screen is missing.
     */
    fun openAutostartSettings(context: Context) {
        val intents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
        )
        for (intent in intents) {
            try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { /* next */ }
        }
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system "Notification access" page, on our own entry where the OS supports it. */
    fun openAccessSettings(context: Context) {
        val component = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in if (Build.VERSION.SDK_INT >= 30) listOf(detail, list) else listOf(list)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (e: ActivityNotFoundException) { /* some OEMs lack the detail page */ }
        }
    }
}
