package app.pwhs.blockads.ui.home.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Staggered entrance animation used across the home page.
 *
 * The wrapped section fades in while sliding up once, on first composition.
 * [index] controls the stagger order — sections further down the page start a
 * little later so the whole page settles in a single smooth cascade instead of
 * everything appearing at once. The animation is purely visual: it uses
 * `graphicsLayer`, so it never affects layout or touch handling.
 */
@Composable
fun Reveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 420,
                delayMillis = index.coerceIn(0, 6) * 70,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Column(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 26.dp.toPx()
        },
        content = content,
    )
}
