package vegabobo.languageselector.ui.components

import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath

// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/res/res/interpolator/fast_out_extra_slow_in.xml
val FastOutExtraSlowInEasing: Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

val ExtraSlowOutFastInEasing: Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.75f, 0f, 0.791667f, 0.18f, 0.833334f, 0.6f)
        cubicTo(0.866667f, 0.94f, 0.95f, 1f, 1f, 1f)
    },
)

@Immutable
class PathEasing(path: Path) : Easing {

    private val delegate = PathInterpolator(path.asAndroidPath())

    override fun transform(fraction: Float): Float =
        delegate.getInterpolation(fraction)
}
