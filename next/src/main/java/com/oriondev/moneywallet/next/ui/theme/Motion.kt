package com.oriondev.moneywallet.next.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens (Material 3 "standard" scheme). Use these instead of ad-hoc springs so the app
 * feels consistently quick: spatial motion settles in ~300 ms, effects (fades, colours) in ~150 ms,
 * and nothing the user waits on takes longer than that.
 */
object Motion {
    /** Movement and size changes of regular components (cards, sheets, list items). */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    /** Small, quick movements (chips, toggles, selection indicators). */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    /** A touch of overshoot for playful emphasis (FAB, success check). Use sparingly. */
    fun <T> expressive(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    /** Opacity / colour changes. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    /** Numbers counting to a new value and chart values growing in. */
    fun <T> data(): FiniteAnimationSpec<T> = tween(durationMillis = 350)
    /** Offset spec for slide transitions (AnimatedContent / navigation). */
    val slide: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))
}
