package com.oriondev.moneywallet.next.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.MoneyBag
import com.oriondev.moneywallet.next.core.MoneyFormat
import com.oriondev.moneywallet.next.data.repo.CurrencyContext
import com.oriondev.moneywallet.next.domain.analytics.Total
import com.oriondev.moneywallet.next.ui.components.AmountText
import com.oriondev.moneywallet.next.ui.components.AmountTint
import com.oriondev.moneywallet.next.ui.components.AnimatedAmountText
import com.oriondev.moneywallet.next.ui.components.MoneyText

/**
 * Shows a [Total] in the base currency; when a rate is missing it falls back to one line per
 * currency and a small hint instead of a wrong number.
 */
@Composable
fun TotalText(
    total: Total,
    ctx: CurrencyContext,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    tint: AmountTint = AmountTint.NEUTRAL,
    animated: Boolean = false,
    compact: Boolean = false,
    signed: Boolean = false,
) {
    val base = total.base
    if (base != null) {
        if (animated) AnimatedAmountText(base, ctx.base, modifier, style, tint, signed = signed, compact = compact)
        else AmountText(base, ctx.base, modifier, style, tint, signed = signed, compact = compact)
    } else {
        Column(modifier) {
            total.bag.entries.filterValues { it != 0L }.ifEmpty { mapOf(ctx.baseIso to 0L) }.forEach { (iso, minor) ->
                AmountText(minor, ctx.currency(iso), style = style, tint = tint, signed = signed, compact = compact)
            }
            RateHint()
        }
    }
}

@Composable
fun RateHint(modifier: Modifier = Modifier) {
    Text("Exchange rate unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Per-currency chips for a bag with more than one currency. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrencyBreakdown(bag: MoneyBag, ctx: CurrencyContext, modifier: Modifier = Modifier) {
    val entries = bag.entries.filterValues { it != 0L }
    if (entries.size < 2) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (iso, minor) ->
            // a plain outlined label: a chip with onClick = {} would be announced as a button
            Surface(shape = MaterialTheme.shapes.small, color = Color.Transparent, border = BorderStroke(1.dp, LocalContentColor.current.copy(alpha = 0.3f))) {
                MoneyText(MoneyFormat.format(minor, ctx.currency(iso), compact = true), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Plain-text amount for share sheets and notifications. */
fun Total.describe(ctx: CurrencyContext): String = base?.let { MoneyFormat.format(it, ctx.base) }
    ?: bag.entries.entries.joinToString(" + ") { MoneyFormat.format(it.value, ctx.currency(it.key)) }

/**
 * Wrapper kept for the screens that list cards with it. Deliberately no entrance choreography:
 * cards appear at once, which reads as fast; [index] is ignored.
 */
@Composable
fun Staggered(@Suppress("UNUSED_PARAMETER") index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) { content() }
}
