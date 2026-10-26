package com.oriondev.moneywallet.next.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

/** M3 type scale with emphasised display styles for money. */
val WalletTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val MoneyDisplay: TextStyle = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
