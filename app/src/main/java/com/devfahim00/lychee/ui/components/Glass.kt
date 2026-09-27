package com.devfahim00.lychee.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devfahim00.lychee.ui.theme.BgBottom
import com.devfahim00.lychee.ui.theme.BgTop
import com.devfahim00.lychee.ui.theme.LycheeCoral
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.LycheeViolet
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary
import kotlin.math.cos
import kotlin.math.sin

private val GlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.06f),
        Color.White.copy(alpha = 0.22f)
    )
)

private val GlassFillBrush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.11f),
        Color.White.copy(alpha = 0.045f)
    ),
    start = Offset(0f, 0f),
    end = Offset(220f, 400f)
)

/** The signature frosted glass card used across Lychee. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    specular: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassFillBrush)
            .border(1.dp, GlassBorderBrush, shape)
    ) {
        content()
        if (specular) {
            // subtle specular highlight along the top edge
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/** Gradient primary button with a glass ring. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val gradient = Brush.linearGradient(listOf(LycheePink, LycheeViolet))
    val disabledGradient = Brush.linearGradient(
        listOf(Color(0x55FF5C8A), Color(0x55B14CFF))
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        modifier = modifier
            .background(if (enabled) gradient else disabledGradient, shape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
    ) {
        leading?.invoke(this)
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

/** Glass-styled text input. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        cursorBrush = Brush.verticalGradient(listOf(LycheePink, LycheeViolet)),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                leading?.invoke()
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = if (leading != null) 10.dp else 0.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextTertiary, fontSize = 15.sp)
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                if (focused) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                brush = if (focused) {
                    Brush.linearGradient(listOf(LycheePink, LycheeViolet))
                } else GlassBorderBrush,
                shape = shape
            )
    )
}

/**
 * Ambient animated gradient-blob background: the soul of the glassy look.
 * Draws soft drifting radial blobs over a deep plum gradient.
 */
@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glassBg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Canvas(modifier = modifier.background(Brush.verticalGradient(listOf(BgTop, BgBottom)))) {
        val w = size.width
        val h = size.height
        val tau = 2f * Math.PI.toFloat()

        // lychee pink blob (top)
        drawBlob(
            color = LycheePink,
            alpha = 0.30f,
            center = Offset(
                x = w * (0.62f + 0.28f * sin(tau * t)),
                y = h * (0.12f + 0.06f * cos(tau * t))
            ),
            radius = w * 0.45f
        )
        // violet blob (mid-left)
        drawBlob(
            color = LycheeViolet,
            alpha = 0.26f,
            center = Offset(
                x = w * (0.14f + 0.10f * sin(tau * t + 1.7f)),
                y = h * (0.52f + 0.10f * cos(tau * t + 0.6f))
            ),
            radius = w * 0.42f
        )
        // coral blob (bottom-right)
        drawBlob(
            color = LycheeCoral,
            alpha = 0.20f,
            center = Offset(
                x = w * (0.82f + 0.08f * cos(tau * t + 0.9f)),
                y = h * (0.80f + 0.08f * sin(tau * t + 2.2f))
            ),
            radius = w * 0.38f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlob(
    color: Color,
    alpha: Float,
    center: Offset,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        style = Fill
    )
}

/** Gradient wordmark text. */
@Composable
fun BrandText(text: String, fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(
            brush = Brush.linearGradient(listOf(LycheePink, LycheeViolet))
        )
    )
}

/** Soft secondary text. */
@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Text(text = text, modifier = modifier, color = TextSecondary, fontSize = fontSize.sp)
}
