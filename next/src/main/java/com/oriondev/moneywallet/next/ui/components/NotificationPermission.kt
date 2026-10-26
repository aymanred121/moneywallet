package com.oriondev.moneywallet.next.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the app may post notifications, and a way to ask. */
@Stable
class NotificationPermission internal constructor(granted: Boolean) {
    internal var launch: () -> Unit = {}
    /** True when notifications can be shown (runtime permission granted and not blocked in system settings). */
    var granted: Boolean by mutableStateOf(granted)
        internal set

    /** Shows the system prompt on Android 13+ when not granted yet; a no-op otherwise. */
    fun request() { if (!granted && Build.VERSION.SDK_INT >= 33) launch() }
}

/**
 * Tracks notification permission for the current screen, refreshed on every resume (the user may
 * come back from system settings). [onResult] receives the prompt's answer.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): NotificationPermission {
    val context = LocalContext.current
    val state = remember { NotificationPermission(notificationsAllowed(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state.granted = notificationsAllowed(context)
        onResult(ok)
    }
    state.launch = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    LifecycleResumeEffect(Unit) {
        state.granted = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    return state
}

fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** The app's notification page in system settings. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
