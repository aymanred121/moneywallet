package com.oriondev.moneywallet.next.ui.components.transactions

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.next.core.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/** Tiny calculator over a display string using + − × ÷ (× ÷ bind tighter). */
object AmountExpression {
    const val PLUS = '+'; const val MINUS = '−'; const val TIMES = '×'; const val DIV = '÷'
    private val ops = setOf(PLUS, MINUS, TIMES, DIV)

    fun isOperator(c: Char) = c in ops
    fun hasOperator(expr: String) = expr.any { it in ops }

    private fun currentNumber(expr: String): String = expr.takeLastWhile { it !in ops }

    fun append(expr: String, key: String, maxDecimals: Int): String {
        if (key.isEmpty()) return expr
        val c = key.first()
        val last = expr.lastOrNull()
        return when {
            c.isDigit() -> {
                val cur = currentNumber(expr)
                val dot = cur.indexOf('.')
                if (dot >= 0 && cur.length - dot - 1 >= maxDecimals) return expr
                if (cur.replace(".", "").length >= 12) return expr
                if (cur == "0") expr.dropLast(1) + c else expr + c
            }
            c == '.' -> {
                if (maxDecimals == 0) return expr
                val cur = currentNumber(expr)
                if (cur.contains('.')) expr else if (cur.isEmpty()) expr + "0." else "$expr."
            }
            c in ops -> {
                if (expr.isEmpty()) return expr
                when {
                    last != null && last in ops -> expr.dropLast(1) + c
                    last == '.' -> expr.dropLast(1) + c
                    else -> expr + c
                }
            }
            else -> expr
        }
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    fun evaluate(expr: String): BigDecimal? {
        var text = expr.trim()
        if (text.isEmpty()) return null
        if (text.last() in ops || text.last() == '.') text = text.dropLast(1)
        if (text.isEmpty()) return null
        val numbers = ArrayList<BigDecimal>()
        val operators = ArrayList<Char>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch in ops) {
                numbers += sb.toString().toBigDecimalOrNull() ?: return null
                sb.clear()
                operators += ch
            } else sb.append(ch)
        }
        numbers += sb.toString().toBigDecimalOrNull() ?: return null
        // pass 1: × ÷
        var i = 0
        while (i < operators.size) {
            val op = operators[i]
            if (op == TIMES || op == DIV) {
                val a = numbers[i]; val b = numbers[i + 1]
                val r = if (op == TIMES) a.multiply(b) else {
                    if (b.signum() == 0) return null
                    a.divide(b, 10, RoundingMode.HALF_UP)
                }
                numbers[i] = r; numbers.removeAt(i + 1); operators.removeAt(i)
            } else i++
        }
        var result = numbers[0]
        for (k in operators.indices) {
            result = if (operators[k] == PLUS) result.add(numbers[k + 1]) else result.subtract(numbers[k + 1])
        }
        return result
    }

    /** Evaluate to minor units of [currency]; null when empty/invalid. */
    fun toMinor(expr: String, currency: Currency): Long? {
        val v = evaluate(expr) ?: return null
        return v.movePointRight(currency.decimals).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Collapse an expression to a plain decimal string. */
    fun simplify(expr: String, maxDecimals: Int): String {
        val v = evaluate(expr) ?: return ""
        return v.setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it.contains('E')) v.toPlainString() else it }
    }

    fun fromMinor(minor: Long, currency: Currency): String =
        BigDecimal(minor).movePointLeft(currency.decimals).stripTrailingZeros().toPlainString().let { if (it == "0") "" else it }
}

/**
 * Key height and gaps by screen height: five rows of 56dp keys take ~40% of a shorter phone
 * (e.g. 1220×2712 at 520dpi is only ~830dp tall) and push the form under the keypad.
 */
internal data class KeySize(val height: Dp, val gap: Dp)

internal fun keySizeFor(screenHeightDp: Int): KeySize = when {
    screenHeightDp < 720 -> KeySize(42.dp, 6.dp)
    screenHeightDp < 880 -> KeySize(48.dp, 6.dp)
    else -> KeySize(56.dp, 8.dp)
}

private val LocalKeySize = staticCompositionLocalOf { KeySize(56.dp, 8.dp) }

/** M3 tonal number pad with the four basic operators; always laid out LTR (7 8 9 ÷), even in RTL locales. */
@Composable
fun AmountKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    doneLabel: String = "Done",
) = CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, LocalKeySize provides keySizeFor(LocalConfiguration.current.screenHeightDp)) {
    val size = LocalKeySize.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = size.gap), verticalArrangement = Arrangement.spacedBy(size.gap)) {
        val rows = listOf(
            listOf("7", "8", "9", AmountExpression.DIV.toString()),
            listOf("4", "5", "6", AmountExpression.TIMES.toString()),
            listOf("1", "2", "3", AmountExpression.MINUS.toString()),
            listOf(".", "0", "⌫", AmountExpression.PLUS.toString()),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.gap)) {
                row.forEach { key ->
                    when {
                        key == "⌫" -> KeyButton(
                            onClick = onBackspace, onLongClick = onClear,
                            container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface,
                        ) { Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace, hold to clear") }
                        key.length == 1 && AmountExpression.isOperator(key[0]) -> KeyButton(
                            onClick = { onKey(key) },
                            container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                        key == "." && !allowDecimal -> Box(Modifier.weight(1f).height(size.height))
                        else -> KeyButton(
                            onClick = { onKey(key) },
                            container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurface,
                        ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            KeyButton(onClick = onDone, container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary) {
                Text(doneLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.KeyButton(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    onLongClick: (() -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.weight(1f).height(LocalKeySize.current.height),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Box(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) { label() }
    }
}
