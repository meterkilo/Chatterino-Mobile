package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.chatterinomobile.data.model.ColorStop
import com.example.chatterinomobile.data.model.GradientFunction
import com.example.chatterinomobile.data.model.Paint as PaintModel
import com.example.chatterinomobile.data.model.Shadow as ShadowModel
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun PaintedUsername(
    name: String,
    fallbackColor: Color,
    paint: PaintModel?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    shadowPadding: Dp = paint.shadowPaddingPx().dp
) {
    val shadows = remember(paint) { paint?.shadows.orEmpty().map { it.toComposeShadow() } }
    val paddedModifier = modifier.padding(horizontal = shadowPadding)

    if (paint is PaintModel.Image) {
        ImagePaintedUsername(
            name = name,
            fallbackColor = fallbackColor,
            paint = paint,
            style = style,
            shadows = shadows,
            modifier = paddedModifier
        )
        return
    }

    val brush = remember(paint, fallbackColor) { paint.toBrush(fallbackColor) }

    val resolved = style.copy(
        brush = brush,
        fontWeight = FontWeight.Bold,
        shadow = null
    )

    Box(modifier = paddedModifier) {
        PaintedUsernameShadows(
            name = name,
            style = style,
            shadows = shadows
        )
        BasicText(text = name, style = resolved)
    }
}

@Composable
private fun PaintedUsernameShadows(
    name: String,
    style: TextStyle,
    shadows: List<Shadow>
) {
    shadows.forEach { shadow ->
        BasicText(
            text = name,
            style = style.copy(
                color = shadow.color,
                fontWeight = FontWeight.Bold,
                shadow = shadow
            )
        )
    }
}

@Composable
private fun ImagePaintedUsername(
    name: String,
    fallbackColor: Color,
    paint: PaintModel.Image,
    style: TextStyle,
    shadows: List<Shadow>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val maskStyle = remember(style) {
        style.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            shadow = null
        )
    }
    val textLayout = remember(name, maskStyle) {
        textMeasurer.measure(text = name, style = maskStyle)
    }
    val density = LocalDensity.current
    val width = with(density) { textLayout.size.width.toDp() }
    val height = with(density) { textLayout.size.height.toDp() }
    val imagePainter = rememberAsyncImagePainter(model = paint.url)

    val fallbackTextStyle = remember(style, fallbackColor) {
        style.copy(
            color = fallbackColor,
            fontWeight = FontWeight.Bold,
            shadow = null
        )
    }

    Box(modifier = modifier.size(width, height)) {
        PaintedUsernameShadows(
            name = name,
            style = style,
            shadows = shadows
        )
        BasicText(text = name, style = fallbackTextStyle)

        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            BasicText(text = name, style = maskStyle)

            Image(
                painter = imagePainter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        blendMode = BlendMode.SrcIn
                    }
            )
        }
    }
}

private fun PaintModel?.toBrush(fallback: Color): Brush = when (this) {
    null -> SolidColor(fallback)
    is PaintModel.Solid -> SolidColor(Color(color))
    is PaintModel.Gradient -> {
        val composeStops: Array<Pair<Float, Color>> = (stops
            .sortedBy { it.at }
            .map { it.toPair() }
            .takeIf { it.isNotEmpty() }
            ?: listOf(0f to fallback, 1f to fallback))
            .toTypedArray()
        when (function) {
            GradientFunction.LINEAR -> linearAt(angle.toFloat(), composeStops)
            GradientFunction.RADIAL -> Brush.radialGradient(colorStops = composeStops)
            GradientFunction.CONIC -> Brush.sweepGradient(colorStops = composeStops)
        }
    }
    is PaintModel.Image -> SolidColor(fallback)
}

internal fun PaintModel?.shadowPaddingPx(): Float =
    this?.shadows.orEmpty().maxOfOrNull { shadow ->
        max(abs(shadow.xOffset), abs(shadow.yOffset)) + shadow.radius * 2f
    }?.coerceIn(0f, MAX_SHADOW_PADDING_PX) ?: 0f

private fun ColorStop.toPair(): Pair<Float, Color> = at to Color(color)

private fun linearAt(angleDeg: Float, stops: Array<Pair<Float, Color>>): Brush {
    val rad = angleDeg * PI.toFloat() / 180f
    val dx = cos(rad)
    val dy = sin(rad)
    val end = Offset(dx * 200f, dy * 200f)
    return Brush.linearGradient(
        colorStops = stops,
        start = Offset.Zero,
        end = end
    )
}

private fun ShadowModel.toComposeShadow(): Shadow = Shadow(
    color = Color(color),
    offset = Offset(xOffset, yOffset),
    blurRadius = radius
)

private const val MAX_SHADOW_PADDING_PX = 12f
