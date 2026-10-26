package com.oriondev.moneywallet.next.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.oriondev.moneywallet.next.core.Currency
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.ui.theme.money
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.oriondev.moneywallet.next.R
import com.oriondev.moneywallet.next.core.CurrencySymbols
import com.oriondev.moneywallet.next.ui.theme.Motion
import java.math.BigDecimal
import java.math.RoundingMode

enum class AmountTint { NEUTRAL, INCOME, EXPENSE, AUTO }

/** True while the user has balances hidden; read by every money text so it recomposes immediately. */
val LocalHideAmounts = compositionLocalOf { false }

private const val RIYAL_INLINE_ID = "riyal"

/** Replaces every Riyal sign with an inline placeholder that [riyalInlineContent] fills with the vector glyph. */
fun String.withCurrencySymbols(): AnnotatedString {
    val source = this
    if (!source.contains(CurrencySymbols.RIYAL_SIGN)) return AnnotatedString(source)
    return buildAnnotatedString {
        var i = 0
        while (i < source.length) {
            val next = source.indexOf(CurrencySymbols.RIYAL_SIGN, i)
            if (next < 0) { append(source.substring(i)); break }
            append(source.substring(i, next))
            appendInlineContent(RIYAL_INLINE_ID, "SAR")
            i = next + 1
        }
    }
}

/** Inline content map for texts produced by [withCurrencySymbols]; the icon takes the text colour and size. */
@Composable
fun riyalInlineContent(): Map<String, InlineTextContent> = remember {
    mapOf(
        RIYAL_INLINE_ID to InlineTextContent(
            Placeholder(width = 0.95.em, height = 1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) {
            Icon(
                painterResource(R.drawable.ic_riyal), contentDescription = "SAR",
                modifier = Modifier.fillMaxSize().padding(horizontal = 0.04.dp),
                tint = LocalContentColor.current,
            )
        },
    )
}

/** Drop-in for Text() when the string may contain a currency symbol. */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
) {
    LocalHideAmounts.current
    Text(text.withCurrencySymbols(), modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow, textAlign = textAlign, fontWeight = fontWeight, inlineContent = riyalInlineContent())
}

/** Formatted money with the app's income/expense colours. */
@Composable
fun AmountText(
    minor: Long,
    currency: Currency,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    tint: AmountTint = AmountTint.NEUTRAL,
    signed: Boolean = false,
    showSymbol: Boolean = true,
    compact: Boolean = false,
    maxLines: Int = 1,
) {
    val colors = MaterialTheme.money
    val color: Color = when (tint) {
        AmountTint.NEUTRAL -> LocalContentColor.current
        AmountTint.INCOME -> colors.income
        AmountTint.EXPENSE -> colors.expense
        AmountTint.AUTO -> if (minor < 0) colors.expense else if (minor > 0) colors.income else LocalContentColor.current
    }
    val hidden = LocalHideAmounts.current || MoneyFormat.masked
    Text(
        MoneyFormat.format(minor, currency, showSymbol = showSymbol, signed = signed, compact = compact, hidden = hidden).withCurrencySymbols(),
        modifier = modifier, style = style, color = if (hidden) LocalContentColor.current else color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        inlineContent = riyalInlineContent(),
    )
}

/**
 * The value [fraction] of the way from [from] to [to], computed exactly in decimal so large minor
 * amounts never pick up Float/Double rounding; 0 and 1 (and anything past them) return the ends.
 */
fun lerpMinor(from: Long, to: Long, fraction: Float): Long = when {
    fraction <= 0f -> from
    fraction >= 1f -> to
    else -> BigDecimal(to).subtract(BigDecimal(from)).multiply(BigDecimal(fraction.toDouble()))
        .setScale(0, RoundingMode.HALF_EVEN).add(BigDecimal(from)).toLong()
}

/** Counts up/down to the target for hero balances; the last frame is exactly [minor]. */
@Composable
fun AnimatedAmountText(
    minor: Long,
    currency: Currency,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    tint: AmountTint = AmountTint.NEUTRAL,
    signed: Boolean = false,
    compact: Boolean = false,
) {
    var from by remember { mutableLongStateOf(minor) }
    var to by remember { mutableLongStateOf(minor) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(minor) {
        if (minor == to) return@LaunchedEffect
        from = lerpMinor(from, to, progress.value) // start from what is on screen now
        to = minor
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.data())
    }
    AmountText(lerpMinor(from, to, progress.value), currency, modifier, style, tint, signed, compact = compact)
}
