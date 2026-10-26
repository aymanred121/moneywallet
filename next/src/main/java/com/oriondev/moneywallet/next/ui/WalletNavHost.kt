package com.oriondev.moneywallet.next.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.oriondev.moneywallet.next.AppContainer
import com.oriondev.moneywallet.next.data.prefs.Settings
import com.oriondev.moneywallet.next.ui.screens.analytics.insightsGraph
import com.oriondev.moneywallet.next.ui.screens.capture.captureGraph
import com.oriondev.moneywallet.next.ui.screens.goals.goalsGraph
import com.oriondev.moneywallet.next.ui.screens.home.homeGraph
import com.oriondev.moneywallet.next.ui.screens.manage.manageGraph
import com.oriondev.moneywallet.next.ui.screens.transactions.transactionsGraph
import com.oriondev.moneywallet.next.ui.theme.Motion

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private val fadeThroughRoots = topLevelDestinations.map { it.route }.toSet() + Routes.ONBOARDING

/** Tab switches (and leaving onboarding) swap whole contexts: fade through rather than slide. */
private fun Scope.isFadeThrough() = initialState.destination.route in fadeThroughRoots && targetState.destination.route in fadeThroughRoots

// Material fade-through, ~250 ms in total: the old screen fades out fast, the new one fades and scales in after it
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 160
private val fadeThroughEnter = fadeIn(tween(FADE_IN_MS, FADE_OUT_MS, LinearOutSlowInEasing)) + scaleIn(tween(FADE_IN_MS, FADE_OUT_MS, LinearOutSlowInEasing), initialScale = 0.96f)
private val fadeThroughExit = fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))

/** Shared X axis: a short slide plus fade, towards Start going forward and End going back (RTL-aware). */
private fun Scope.axisEnter(towards: AnimatedContentTransitionScope.SlideDirection): EnterTransition =
    slideIntoContainer(towards, Motion.slide) { it / 10 } + fadeIn(Motion.effects())
private fun Scope.axisExit(towards: AnimatedContentTransitionScope.SlideDirection): ExitTransition =
    slideOutOfContainer(towards, Motion.slide) { it / 10 } + fadeOut(Motion.effects())

@Composable
fun WalletNavHost(navController: NavHostController, container: AppContainer, settings: Settings, modifier: Modifier = Modifier) {
    // Fixed for the host's lifetime: onboarding navigates to home itself, and a start destination
    // flipping underneath it would reset the graph and play a second transition.
    val start = remember { if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING }
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier,
        enterTransition = { if (isFadeThrough()) fadeThroughEnter else axisEnter(AnimatedContentTransitionScope.SlideDirection.Start) },
        exitTransition = { if (isFadeThrough()) fadeThroughExit else axisExit(AnimatedContentTransitionScope.SlideDirection.Start) },
        popEnterTransition = { if (isFadeThrough()) fadeThroughEnter else axisEnter(AnimatedContentTransitionScope.SlideDirection.End) },
        popExitTransition = { if (isFadeThrough()) fadeThroughExit else axisExit(AnimatedContentTransitionScope.SlideDirection.End) },
    ) {
        homeGraph(navController)
        insightsGraph(navController)
        transactionsGraph(navController)
        manageGraph(navController)
        // feature graphs (captures, goals) register below, one line each
        captureGraph(navController)
        goalsGraph(navController)
    }
}
