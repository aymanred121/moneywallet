package com.oriondev.moneywallet.next.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.data.prefs.ThemeMode

/** Semantic money colours that stay stable regardless of the dynamic scheme. */
data class MoneyColors(val income: Color, val expense: Color, val incomeContainer: Color, val expenseContainer: Color)

val LocalMoneyColors = staticCompositionLocalOf { lightMoneyColors }

private val lightMoneyColors = MoneyColors(Color(0xFF1B6B47), Color(0xFFB3261E), Color(0xFFCDEBDA), Color(0xFFF9DEDC))
private val darkMoneyColors = MoneyColors(Color(0xFF7DD4A6), Color(0xFFF2B8B5), Color(0xFF1F4B36), Color(0xFF5C2523))

private val seedLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C4C), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFF89F8C7), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFCFE9D9), onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3D6373), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFC1E8FB), onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF5), onBackground = Color(0xFF171D1A), surface = Color(0xFFF5FBF5), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF404943), outline = Color(0xFF707973), outlineVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF5F0), surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3EAE4), surfaceContainerHighest = Color(0xFFDDE4DE), inverseSurface = Color(0xFF2C322E), inverseOnSurface = Color(0xFFECF2EC), inversePrimary = Color(0xFF6CDBAC),
)

private val seedDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF6CDBAC), onPrimary = Color(0xFF003825), primaryContainer = Color(0xFF005138), onPrimaryContainer = Color(0xFF89F8C7),
    secondary = Color(0xFFB3CCBD), onSecondary = Color(0xFF1F352A), secondaryContainer = Color(0xFF354B40), onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CCDE), onTertiary = Color(0xFF073543), tertiaryContainer = Color(0xFF244C5B), onTertiaryContainer = Color(0xFFC1E8FB),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4DF), surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404943), onSurfaceVariant = Color(0xFFBFC9C2), outline = Color(0xFF89938C), outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A), surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28), surfaceContainerHighest = Color(0xFF303633), inverseSurface = Color(0xFFDEE4DF), inverseOnSurface = Color(0xFF2C322E), inversePrimary = Color(0xFF006C4C),
)

val WalletShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MoneyWalletTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> seedDark
        else -> seedLight
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides if (dark) darkMoneyColors else lightMoneyColors) {
        MaterialTheme(colorScheme = scheme, shapes = WalletShapes, typography = WalletTypography, content = content)
    }
}

/** The in-app theme choice resolved against the device setting; also drives the system bar icons. */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }

val MaterialTheme.money: MoneyColors @Composable get() = LocalMoneyColors.current
