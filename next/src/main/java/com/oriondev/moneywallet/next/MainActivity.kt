package com.oriondev.moneywallet.next

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oriondev.moneywallet.next.ui.AppRoot
import com.oriondev.moneywallet.next.ui.lock.LockGate
import com.oriondev.moneywallet.next.ui.theme.MoneyWalletTheme
import com.oriondev.moneywallet.next.ui.theme.isDarkTheme

class MainActivity : FragmentActivity() {

    /** Route requested by a widget, notification or deep link; consumed once by AppRoot. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // a recreated activity (rotation, process restore) already handled its launch route
        if (savedInstanceState == null) pendingRoute = routeFrom(intent)
        val container = WalletApp.container(this)
        var loaded = false
        // nothing may render on defaults: the lock, the onboarding switch and the balance mask
        // all depend on the stored settings, so the splash stays up until they are read
        splash.setKeepOnScreenCondition { !loaded }
        setContent {
            val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            loaded = true
            // bar icons follow the in-app theme choice, not only the device setting
            val dark = isDarkTheme(current.themeMode)
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
            }
            MoneyWalletTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                LockGate(enabled = current.biometricLock) {
                    AppRoot(
                        container = container,
                        settings = current,
                        pendingRoute = pendingRoute,
                        onRouteConsumed = { pendingRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?): String? {
        intent?.getStringExtra(EXTRA_ROUTE)?.let { route -> return route.takeIf(::isAllowedExternalRoute) }
        val data = intent?.data ?: return null
        return if (data.scheme == "moneywallet" && data.host == "add") "transaction/new?walletId=-1&direction=0" else null
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        // androidx.activity's default scrims for 3-button navigation
        private val LIGHT_NAV_SCRIM = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_NAV_SCRIM = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

        /**
         * The activity is exported, so any app can send a "route" extra. Only screens our own
         * widgets and notifications open are accepted; add a prefix here when adding one.
         */
        private val EXTERNAL_ROUTE_PREFIXES = listOf("transaction/new?", "review/", "home", "transactions", "budgets", "captures")

        fun isAllowedExternalRoute(route: String): Boolean =
            // debug builds accept any screen so the screenshot script can open it over adb
            BuildConfig.DEBUG || (EXTERNAL_ROUTE_PREFIXES.any { route.startsWith(it) } && !route.contains(".."))
    }
}
