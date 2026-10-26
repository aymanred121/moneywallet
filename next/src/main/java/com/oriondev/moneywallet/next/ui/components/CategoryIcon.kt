package com.oriondev.moneywallet.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.oriondev.moneywallet.next.core.IconSpec
import com.oriondev.moneywallet.next.data.repo.IconCatalog

/** Renders a legacy-compatible icon spec: a coloured circle with letters, or a bundled vector. */
@Composable
fun SpecIcon(spec: IconSpec, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    when (spec) {
        is IconSpec.Letter -> {
            val color = parseColor(spec.color)
            Box(
                modifier.size(size).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    spec.text.take(2),
                    color = bestOnColor(color),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.38f).sp,
                    maxLines = 1,
                )
            }
        }
        is IconSpec.Symbol -> {
            val vector = SymbolIcons.vector(spec.key)
            if (vector == null) SpecIcon(IconSpec.Letter(spec.color, spec.text), size, modifier)
            else {
                val color = parseColor(spec.color)
                Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(vector, contentDescription = null, tint = bestOnColor(color), modifier = Modifier.size(size * 0.56f))
                }
            }
        }
        is IconSpec.Resource -> {
            val id = IconCatalog.resId(spec.name)
            if (id == 0) {
                val symbol = SymbolIcons.legacyNames[spec.name]
                val fallbackColor = IconSpec.palette[(spec.name.hashCode() and 0x7fffffff) % IconSpec.palette.size]
                SpecIcon(
                    if (symbol != null) IconSpec.Symbol(symbol, fallbackColor) else IconSpec.Letter("#5C6BC0", spec.name.removePrefix("ic_icon_").take(2)),
                    size, modifier,
                )
            } else {
                Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    val painter: Painter = painterResource(id)
                    Image(painter, contentDescription = null, modifier = Modifier.size(size).padding(size * 0.12f), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
fun SpecIcon(encoded: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) = SpecIcon(IconSpec.parse(encoded), size, modifier)

fun parseColor(hex: String): Color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color(0xFF5C6BC0) }

fun bestOnColor(bg: Color): Color = if (bg.luminance() > 0.55f) Color(0xDE000000) else Color.White

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
