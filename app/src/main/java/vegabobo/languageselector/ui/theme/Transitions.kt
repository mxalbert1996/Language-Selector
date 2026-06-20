package vegabobo.languageselector.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import vegabobo.languageselector.ui.components.FastOutExtraSlowInEasing

@Immutable
class Transitions(density: Density) {

    val enter = fadeIn(
        initialAlpha = 0f,
        animationSpec = tween(
            durationMillis = 83,
            delayMillis = 50,
            easing = LinearEasing,
        ),
    ) + slideInHorizontally(
        initialOffsetX = { with(density) { 96.dp.roundToPx() } },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutExtraSlowInEasing,
        ),
    )

    val exit = fadeOut(
        targetAlpha = 0f,
        animationSpec = tween(
            durationMillis = 83,
            delayMillis = 50,
            easing = LinearEasing,
        ),
    ) + slideOutHorizontally(
        targetOffsetX = { with(density) { -96.dp.roundToPx() } },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutSlowInEasing,
        ),
    )

    val popEnter = fadeIn(
        initialAlpha = 0f,
        animationSpec = tween(
            durationMillis = 83,
            delayMillis = 35,
            easing = LinearEasing,
        ),
    ) + slideInHorizontally(
        initialOffsetX = { with(density) { -96.dp.roundToPx() } },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutExtraSlowInEasing,
        ),
    )

    val popExit = fadeOut(
        targetAlpha = 0f,
        animationSpec = tween(
            durationMillis = 83,
            delayMillis = 35,
            easing = LinearEasing,
        ),
    ) + slideOutHorizontally(
        targetOffsetX = { with(density) { 96.dp.roundToPx() } },
        animationSpec = tween(
            durationMillis = 450,
            easing = FastOutExtraSlowInEasing,
        ),
    )
}
