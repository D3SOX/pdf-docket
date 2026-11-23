package me.d3sox.pdfwallet.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope

private const val DURATION_MS = 400
private const val PREDICTIVE_DURATION_MS = 350
private const val PREDICTIVE_DELAY_MS = 66
private const val PREDICTIVE_FADE_MS = 75
private const val PREDICTIVE_SLIDE_MS = 200
private const val PREDICTIVE_SCALE_MS = 250
private const val PREDICTIVE_SCALE = 0.9f
private const val PREDICTIVE_ALPHA = 0.75f
private val LEAVING_CORNER_RADIUS = 32.dp
private const val CORNER_DURATION_MS = 75

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
 * While the back gesture is held, the screen shrinks as it follows the finger towards the end, and the previous screen
 * grows in behind it from the start. A gesture that goes through finishes the same movement.
 */
fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.predictiveBackTransition(): ContentTransform =
    (slideInHorizontally(tween(PREDICTIVE_DURATION_MS, easing = FastOutSlowInEasing)) { -it / PARALLAX } +
        fadeIn(tween(PREDICTIVE_FADE_MS, PREDICTIVE_DELAY_MS, LinearEasing), initialAlpha = PREDICTIVE_ALPHA) +
        scaleIn(
            tween(PREDICTIVE_DURATION_MS, PREDICTIVE_DELAY_MS, LinearEasing),
            initialScale = PREDICTIVE_SCALE,
        ) togetherWith
        slideOutHorizontally(tween(PREDICTIVE_SLIDE_MS, easing = FastOutSlowInEasing)) { it } +
        scaleOut(tween(PREDICTIVE_SCALE_MS, easing = LinearEasing), targetScale = PREDICTIVE_SCALE))
        .apply { targetContentZIndex = -1f }

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
    Box(
        Modifier
            .fillMaxSize()
            // read while drawing, so the animation doesn't recompose the screen every frame
            .graphicsLayer {
                shape = RoundedCornerShape(radius)
                clip = true
            }
            .background(MaterialTheme.colorScheme.background),
    ) {
        entry.Content()
    }
}
