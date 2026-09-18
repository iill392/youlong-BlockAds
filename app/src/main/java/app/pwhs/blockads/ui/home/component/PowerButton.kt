package app.pwhs.blockads.ui.home.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pwhs.blockads.R
import app.pwhs.blockads.ui.theme.AccentBlue
import app.pwhs.blockads.ui.theme.DangerRed
import app.pwhs.blockads.ui.theme.SecurityOrange

/**
 * The home page power control.
 *
 * A large circular toggle wrapped in a breathing halo. Three details carry the
 * "alive" feeling: the halo gently breathes while protection is on, a thin arc
 * spins around the button while it is starting or stopping, and the button
 * squishes slightly on touch. All state semantics (role, content description,
 * state description) are unchanged.
 */
@Composable
fun PowerButton(
    isActive: Boolean,
    isConnecting: Boolean,
    isStopping: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = isConnecting || isStopping
    val vpnStateDescription = when {
        isStopping -> stringResource(R.string.status_disconnecting)
        isConnecting -> stringResource(R.string.accessibility_vpn_connecting)
        isActive -> stringResource(R.string.accessibility_vpn_active)
        else -> stringResource(R.string.accessibility_vpn_inactive)
    }
    val toggleDescription = stringResource(R.string.accessibility_toggle_vpn)

    val buttonColor by animateColorAsState(
        targetValue = when {
            isStopping -> SecurityOrange
            isConnecting -> AccentBlue
            isActive -> MaterialTheme.colorScheme.primary
            else -> DangerRed
        },
        animationSpec = tween(500),
        label = "buttonColor"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = when {
            isBusy -> 0.34f
            isActive -> 0.5f
            else -> 0.2f
        },
        animationSpec = tween(500),
        label = "glow"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isActive || isBusy -> 1f
            else -> 0.96f
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scale"
    )

    // Breathing halo — only animates while protection is actually running.
    val breathe = rememberInfiniteTransition(label = "breathe")
    val haloScale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive && !isBusy) 1.07f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    // Spinning arc while connecting / disconnecting.
    val spin = rememberInfiniteTransition(label = "spin")
    val spinAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(196.dp)
    ) {
        // Outer halo (radial glow)
        Box(
            modifier = Modifier
                .size(184.dp)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = glowAlpha
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(buttonColor.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        // Thin breathing outline
        Box(
            modifier = Modifier
                .size(178.dp)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                }
                .border(
                    width = 1.dp,
                    color = buttonColor.copy(alpha = 0.18f),
                    shape = CircleShape
                )
        )

        // Spinning progress arc while starting / stopping
        if (isBusy) {
            Canvas(modifier = Modifier.size(168.dp)) {
                rotate(degrees = spinAngle) {
                    drawArc(
                        color = buttonColor,
                        startAngle = 0f,
                        sweepAngle = 96f,
                        useCenter = false,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }

        // Focus ring (visible only while focused via D-pad/keyboard)
        if (isFocused) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = CircleShape
                    )
            )
        }

        // Main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(144.dp)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .shadow(
                    elevation = if (isActive || isBusy) 22.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = buttonColor.copy(alpha = 0.35f),
                    spotColor = buttonColor.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            buttonColor.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            buttonColor,
                            buttonColor.copy(alpha = 0.25f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isBusy
                ) { onClick() }
                .semantics {
                    contentDescription = toggleDescription
                    stateDescription = vpnStateDescription
                    role = Role.Button
                }
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = buttonColor,
                modifier = Modifier.size(62.dp)
            )
        }
    }
}
