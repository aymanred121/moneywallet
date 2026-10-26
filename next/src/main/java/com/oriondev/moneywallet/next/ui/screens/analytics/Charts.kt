package com.oriondev.moneywallet.next.ui.screens.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.oriondev.moneywallet.next.ui.theme.Motion
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

data class DonutSlice(val key: Any?, val fraction: Float, val color: Color, val label: String = "")

/**
 * Hand-drawn donut. Arcs sweep in on first composition and when [animationKey] changes (pass the
 * period), not on every data update; a tap on a slice reports its key. [center] is drawn in the
 * hole (usually the total). [description] is read by screen readers (defaults to the slice shares).
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    selectedKey: Any? = null,
    onSliceTap: ((Any?) -> Unit)? = null,
    animationKey: Any? = Unit,
    description: String? = null,
    center: @Composable () -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(animationKey) { sweep.snapTo(0f); sweep.animateTo(1f, Motion.data()) }
    val summary = description ?: slices.filter { it.label.isNotEmpty() }.joinToString { "${it.label} ${(it.fraction * 100).toInt()}%" }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val gap = 2f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = summary }.pointerInput(slices) {
                if (onSliceTap == null) return@pointerInput
                detectTapGestures { pos ->
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val radius = minOf(cx, cy)
                    val d = hypot(pos.x - cx, pos.y - cy)
                    if (d < radius * 0.45f || d > radius) return@detectTapGestures
                    var angle = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat() + 90f
                    if (angle < 0) angle += 360f
                    var start = 0f
                    for (s in slices) {
                        val end = start + s.fraction * 360f
                        if (angle >= start && angle < end) { onSliceTap(s.key); return@detectTapGestures }
                        start = end
                    }
                }
            },
        ) {
            val stroke = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            val total = 360f * sweep.value
            for (s in slices) {
                val full = s.fraction * 360f
                val remaining = total - (start + 90f)
                if (remaining <= 0f) break
                val drawn = minOf(full, remaining)
                val selected = selectedKey != null && selectedKey == s.key
                val width = if (selected) stroke * 1.25f else stroke
                val alpha = if (selectedKey != null && !selected) 0.45f else 1f
                drawArc(
                    s.color.copy(alpha = alpha), start + gap / 2, max(drawn - gap, 0f), false,
                    if (selected) topLeft - Offset(width / 8, width / 8) else topLeft,
                    if (selected) Size(diameter + width / 4, diameter + width / 4) else arcSize,
                    style = Stroke(width),
                )
                start += full
            }
        }
        center()
    }
}

data class BarValue(val label: String, val value: Long, val highlighted: Boolean = false)

/**
 * Bars grow from the baseline on first composition and when [animationKey] changes; the
 * highlighted bar (today) uses [highlightColor]; tapping a bar shows a tooltip with the formatted
 * value. [average] draws a dashed reference line. Screen readers get every bar's label and value.
 */
@Composable
fun BarChart(
    bars: List<BarValue>,
    modifier: Modifier = Modifier,
    formatValue: (Long) -> String,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightColor: Color = MaterialTheme.colorScheme.tertiary,
    maxLabels: Int = 8,
    animationKey: Any? = Unit,
    average: Long? = null,
    description: String? = null,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(animationKey) { grow.snapTo(0f); grow.animateTo(1f, Motion.data()) }
    val summary = description ?: (bars.joinToString { "${it.label}: ${formatValue(it.value)}" } + (average?.let { ". Average ${formatValue(it)}" } ?: ""))
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selected by remember(bars) { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier.semantics { contentDescription = summary }.pointerInput(bars) {
            detectTapGestures { pos ->
                if (bars.isEmpty()) return@detectTapGestures
                val slot = size.width / bars.size.toFloat()
                val idx = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                selected = if (selected == idx) -1 else idx
            }
        },
    ) {
        if (bars.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val tipSpace = 28.dp.toPx()
        val chartHeight = size.height - labelHeight - tipSpace
        val slot = size.width / bars.size
        val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val maxValue = maxOf(bars.maxOf { it.value }, average ?: 0L).coerceAtLeast(1L).toFloat()
        val labelEvery = max(1, bars.size / maxLabels)
        bars.forEachIndexed { i, bar ->
            val x = slot * i + (slot - barWidth) / 2
            val h = (bar.value / maxValue) * chartHeight * grow.value
            val top = tipSpace + chartHeight - h
            drawRoundRect(trackColor, Offset(x, tipSpace), Size(barWidth, chartHeight), CornerRadius(barWidth / 2))
            if (h > 0f) {
                val color = when { i == selected -> highlightColor; bar.highlighted -> highlightColor.copy(alpha = 0.8f); else -> barColor }
                drawRoundRect(color, Offset(x, top), Size(barWidth, h), CornerRadius(barWidth / 2))
            }
            if (i % labelEvery == 0 || bar.highlighted) {
                val layout = measurer.measure(bar.label, labelStyle)
                drawText(layout, topLeft = Offset(x + barWidth / 2 - layout.size.width / 2, size.height - labelHeight + 2.dp.toPx()))
            }
        }
        if (average != null && average > 0) {
            val y = tipSpace + chartHeight - (average / maxValue) * chartHeight * grow.value
            drawLine(averageColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
        if (selected in bars.indices) {
            val bar = bars[selected]
            val layout = measurer.measure(formatValue(bar.value), tipStyle)
            val pad = 6.dp.toPx()
            val w = layout.size.width + pad * 2
            val hgt = layout.size.height + pad
            val cx = slot * selected + slot / 2
            val left = (cx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            drawRoundRect(tipBg, Offset(left, 0f), Size(w, hgt), CornerRadius(hgt / 2))
            drawText(layout, topLeft = Offset(left + pad, pad / 2))
        }
    }
}

/** Two horizontal bars (this vs previous period) sharing one scale. */
@Composable
fun CompareBars(
    current: Long,
    previous: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.data()) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val cur by androidx.compose.animation.core.animateFloatAsState(current.toFloat(), Motion.data(), label = "current")
    val prev by androidx.compose.animation.core.animateFloatAsState(previous.toFloat(), Motion.data(), label = "previous")
    Canvas(modifier) {
        val current = cur; val previous = prev
        val maxV = max(current, previous).coerceAtLeast(1f)
        val h = (size.height - 8.dp.toPx()) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(track, Offset.Zero, Size(size.width, h), r)
        drawRoundRect(color, Offset.Zero, Size(size.width * (current / maxV) * grow.value, h), r)
        val y2 = h + 8.dp.toPx()
        drawRoundRect(track, Offset(0f, y2), Size(size.width, h), r)
        drawRoundRect(color.copy(alpha = 0.45f), Offset(0f, y2), Size(size.width * (previous / maxV) * grow.value, h), r)
    }
}

/**
 * Line with a soft area fill, one point per [points] entry (e.g. balance at each cycle end). Tap a
 * point for its value; the last point is marked. Grows in once, on first composition.
 */
@Composable
fun AreaChart(
    points: List<BarValue>,
    modifier: Modifier = Modifier,
    formatValue: (Long) -> String,
    color: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
    maxLabels: Int = 6,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.data()) }
    var selected by remember(points) { mutableIntStateOf(-1) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val summary = description ?: points.joinToString { "${it.label}: ${formatValue(it.value)}" }
    Canvas(
        modifier.semantics { contentDescription = summary }.pointerInput(points) {
            detectTapGestures { pos ->
                if (points.size < 2) return@detectTapGestures
                val step = size.width / (points.size - 1).toFloat()
                val idx = ((pos.x + step / 2) / step).toInt().coerceIn(0, points.lastIndex)
                selected = if (selected == idx) -1 else idx
            }
        },
    ) {
        if (points.size < 2) return@Canvas
        val labelHeight = 18.dp.toPx()
        val tipSpace = 28.dp.toPx()
        val inset = 4.dp.toPx()
        val chartTop = tipSpace
        val chartHeight = size.height - labelHeight - tipSpace - inset
        val minV = minOf(points.minOf { it.value }, 0L).toFloat()
        val maxV = points.maxOf { it.value }.toFloat().let { if (it <= minV) minV + 1f else it }
        val step = size.width / (points.size - 1)
        fun y(v: Long) = chartTop + chartHeight - ((v - minV) / (maxV - minV)) * chartHeight * grow.value
        if (minV < 0f) drawLine(zeroColor, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(0f, y(p.value)) else line.lineTo(step * i, y(p.value)) }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, chartTop + chartHeight)
            lineTo(0f, chartTop + chartHeight)
            close()
        }
        drawPath(area, color.copy(alpha = 0.14f))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        val last = points.lastIndex
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(step * last, y(points[last].value)))
        val labelEvery = max(1, points.size / maxLabels)
        points.forEachIndexed { i, p ->
            if ((last - i) % labelEvery == 0) {
                val layout = measurer.measure(p.label, labelStyle)
                val x = (step * i - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, size.height - labelHeight + 2.dp.toPx()))
            }
        }
        if (selected in points.indices) {
            val p = points[selected]
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(step * selected, y(p.value)))
            val layout = measurer.measure(formatValue(p.value), tipStyle)
            val pad = 6.dp.toPx()
            val w = layout.size.width + pad * 2
            val hgt = layout.size.height + pad
            val left = (step * selected - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            drawRoundRect(tipBg, Offset(left, 0f), Size(w, hgt), CornerRadius(hgt / 2))
            drawText(layout, topLeft = Offset(left + pad, pad / 2))
        }
    }
}
