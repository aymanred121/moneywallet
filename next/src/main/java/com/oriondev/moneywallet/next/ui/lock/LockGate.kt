package com.oriondev.moneywallet.next.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val RELOCK_AFTER_MS = 30_000L
private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Lock state lives in the process, not in saved instance state: a rotation keeps the app
 * unlocked, while a new process (cold start, or restore after the system killed it) always
 * starts locked. Restoring an "unlocked" flag from a saved bundle would bypass the prompt.
 */
private object AppLock {
    var unlocked by mutableStateOf(false)
    var backgroundedAt = 0L
}

/**
 * Wraps [content] behind a biometric / device-credential prompt when [enabled]. Locks on every
 * cold start and again whenever the app spent more than 30 seconds in the background. [enabled]
 * must be the loaded setting, not a default: the caller keeps the splash screen up until then.
 */
@Composable
fun LockGate(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // turning the lock on from Settings must not lock the user out mid-session
    var armed by remember { mutableStateOf(enabled) }
    val locked = enabled && armed && !AppLock.unlocked
    var prompting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enabled) { if (enabled && !armed) { AppLock.unlocked = true; armed = true } }

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (enabled && !prompting) AppLock.backgroundedAt = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> if (enabled && AppLock.backgroundedAt > 0 && System.currentTimeMillis() - AppLock.backgroundedAt > RELOCK_AFTER_MS) {
                    AppLock.unlocked = false; AppLock.backgroundedAt = 0
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // keep balances out of the recent-apps thumbnail while the lock is on (Android 13+)
    DisposableEffect(enabled) {
        val activity = context.findFragmentActivity()
        if (android.os.Build.VERSION.SDK_INT >= 33) activity?.setRecentsScreenshotEnabled(!enabled)
        onDispose { }
    }

    fun authenticate() {
        val activity = context.findFragmentActivity() ?: run { error = "Cannot show the unlock prompt"; return }
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Nothing enrolled any more: do not lock the user out of their own data.
            AppLock.unlocked = true; return
        }
        prompting = true
        error = null
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; AppLock.unlocked = true }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_CANCELED) error = errString.toString()
            }
            override fun onAuthenticationFailed() { /* prompt keeps showing */ }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MoneyWallet")
            .setSubtitle("Confirm it is you to see your finances")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(locked, enabled) { if (locked && enabled && !prompting) authenticate() }

    Box(Modifier.fillMaxSize()) {
        if (!locked) content()
        AnimatedVisibility(visible = locked, enter = fadeIn(), exit = fadeOut() + scaleOut(targetScale = 1.05f)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("MoneyWallet is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "Use your fingerprint, face or device PIN to continue.", style = MaterialTheme.typography.bodyMedium, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { authenticate() }, enabled = !prompting) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Unlock")
                    }
                }
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c != null) {
        if (c is FragmentActivity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}
