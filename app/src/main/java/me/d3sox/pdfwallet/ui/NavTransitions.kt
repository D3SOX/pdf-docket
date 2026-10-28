package me.d3sox.pdfwallet.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent

private const val DURATION_MS = 400
private const val PREDICTIVE_DURATION_MS = 300
private const val PREDICTIVE_SCALE = 0.9f
private val LEAVING_CORNER_RADIUS = 32.dp
private const val CORNER_DURATION_MS = PREDICTIVE_DURATION_MS / 4

/** How far the screen below moves along, as a fraction of its width */
private const val PARALLAX = 4

/**
 * The new screen slides in from the end over the previous one, which moves a bit towards the start.
 */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.forwardTransition(): ContentTransform =
    slideInHorizontally(tween(DURATION_MS)) { it } togetherWith
        slideOutHorizontally(tween(DURATION_MS)) { -it / PARALLAX }

/**
 * The reverse of [forwardTransition], with the previous screen below the leaving one.
 */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.backTransition(): ContentTransform =
    (slideInHorizontally(tween(DURATION_MS)) { -it / PARALLAX } togetherWith
        slideOutHorizontally(tween(DURATION_MS)) { it })
        .apply { targetContentZIndex = -1f }

/**
 * Material's predictive back for full screens: while swiping, the screen shrinks towards the side the gesture
 * started from and rounds its corners (see [roundedWhileLeaving]), revealing the previous screen below.
 * It fades out once the gesture goes through.
 */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.predictiveBackTransition(swipeEdge: Int): ContentTransform {
    val pivotX = if (swipeEdge == NavigationEvent.EDGE_RIGHT) 0f else 1f
    return (slideInHorizontally(tween(PREDICTIVE_DURATION_MS, easing = LinearEasing)) { -it / PARALLAX } togetherWith
        scaleOut(
            tween(PREDICTIVE_DURATION_MS, easing = LinearEasing),
            targetScale = PREDICTIVE_SCALE,
            transformOrigin = TransformOrigin(pivotX, pivotFractionY = 0.5f),
        ) + fadeOut(tween(PREDICTIVE_DURATION_MS / 2, delayMillis = PREDICTIVE_DURATION_MS / 2)))
        .apply { targetContentZIndex = -1f }
}

/**
 * Rounds the corners of screens while they leave, so a shrinking screen looks like a card above the previous one.
 */
fun <T : Any> roundedWhileLeaving(): NavEntryDecorator<T> = NavEntryDecorator { entry ->
    val transition = LocalNavAnimatedContentScope.current.transition
    val radius by transition.animateDp(
        // reaches the full radius early in the gesture
        transitionSpec = { tween(CORNER_DURATION_MS) },
        label = "cornerRadius",
    ) { if (it == EnterExitState.PostExit) LEAVING_CORNER_RADIUS else 0.dp }
    Box(Modifier.clip(RoundedCornerShape(radius))) {
        entry.Content()
    }
}
