package com.example.chatterinomobile.ui.brand

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.theme.PublicSansFontFamily

@Composable
internal fun HolographicSevenTvWordmark(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "holographicSevenTvWordmark")
    val shimmerPhase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            initialStartOffset = StartOffset(180)
        ),
        label = "sevenTvWordmarkShimmer"
    )
    val textBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFBDFBE8),
            Color(0xFFFFF1B7),
            Color(0xFFF3A8E7),
            Color(0xFFB9C2FF),
            Color(0xFFBDFBE8)
        ),
        start = Offset(160f * shimmerPhase - 90f, 0f),
        end = Offset(160f * shimmerPhase + 60f, 60f)
    )

    Box(
        modifier = modifier
            .width(116.dp)
            .height(56.dp)
    ) {
        HolographicSevenTvLogo(
            size = 56.dp,
            floating = false,
            shimmer = true,
            contentScale = 0.88f,
            opticalOffsetX = 0f,
            opticalOffsetY = -0.02f,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "mobile",
            style = TextStyle(
                brush = textBrush,
                fontSize = 12.5.sp,
                fontFamily = PublicSansFontFamily,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.7).sp
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 41.dp, y = 29.dp)
                .graphicsLayer {
                    scaleX = 1.16f
                    scaleY = 0.92f
                }
        )
    }
}

@Composable
internal fun HolographicSevenTvLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    shimmer: Boolean = true,
    contentScale: Float = 0.84f,
    opticalOffsetX: Float = -0.09f,
    opticalOffsetY: Float = -0.085f
) {
    val transition = rememberInfiniteTransition(label = "holographicSevenTv")
    val floatOffset by transition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sevenTvFloat"
    )
    val shimmerPhase by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            initialStartOffset = StartOffset(180)
        ),
        label = "sevenTvShimmer"
    )
    val tracePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            initialStartOffset = StartOffset(120)
        ),
        label = "sevenTvTrace"
    )
    val logoPaths = remember { sevenTvLogoPaths() }

    Canvas(
        modifier = modifier
            .size(size)
            .offset(y = if (floating) floatOffset.dp else 0.dp)
    ) {
        val scale = minOf(
            this.size.width / LOGO_BOUNDS_WIDTH,
            this.size.height / LOGO_BOUNDS_HEIGHT
        ) * contentScale
        val horizontalInset = this.size.width * (0.5f + opticalOffsetX) - LOGO_BOUNDS_CENTER_X * scale
        val verticalInset = this.size.height * (0.5f + opticalOffsetY) - LOGO_BOUNDS_CENTER_Y * scale
        val shimmerX = this.size.width * if (shimmer) shimmerPhase else 0.55f
        val fillBrush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF7B5EC),
                Color(0xFFC4FFE9),
                Color(0xFFFFF1B7),
                Color(0xFFB9C2FF),
                Color(0xFFF3A8E7)
            ),
            start = Offset(shimmerX - this.size.width * 0.78f, 0f),
            end = Offset(shimmerX + this.size.width * 0.42f, this.size.height)
        )
        val glowBrush = Brush.linearGradient(
            colors = listOf(Color(0x663AEAFF), Color(0x66FF8AE8)),
            start = Offset.Zero,
            end = Offset(this.size.width, this.size.height)
        )
        val traceLength = this.size.width * 0.92f
        val outlineTrace = PathEffect.dashPathEffect(
            intervals = floatArrayOf(traceLength * 0.24f, traceLength),
            phase = -tracePhase * traceLength
        )

        withTransform({
            translate(left = horizontalInset, top = verticalInset)
            scale(scaleX = scale, scaleY = scale)
        }) {
            logoPaths.forEach { path ->
                drawPath(
                    path = path,
                    brush = glowBrush,
                    alpha = 0.46f,
                    style = Stroke(width = 7.5f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                )
            }
        }

        withTransform({
            translate(left = horizontalInset, top = verticalInset)
            scale(scaleX = scale, scaleY = scale)
        }) {
            logoPaths.forEach { path ->
                drawPath(path = path, brush = fillBrush)
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.72f),
                    style = Stroke(width = 1.6f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = Color.White,
                    alpha = 0.82f,
                    style = Stroke(
                        width = 2.8f,
                        pathEffect = outlineTrace,
                        join = StrokeJoin.Round,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

private fun sevenTvLogoPaths(): List<Path> = listOf(
    Path().apply {
        moveTo(90.986f, 45.953f)
        lineTo(95.92f, 37.35f)
        lineTo(98.584f, 32.801f)
        lineTo(93.65f, 24.198f)
        lineTo(93.65f, 24f)
        lineTo(67.304f, 24f)
        lineTo(77.171f, 41.206f)
        lineTo(79.934f, 45.953f)
        close()
    },
    Path().apply {
        moveTo(36.616f, 103.703f)
        lineTo(66.218f, 52.084f)
        lineTo(69.869f, 45.755f)
        lineTo(60.002f, 28.549f)
        lineTo(57.239f, 24.099f)
        lineTo(15.598f, 24.099f)
        lineTo(10.664f, 32.702f)
        lineTo(8f, 37.251f)
        lineTo(12.934f, 45.854f)
        lineTo(12.934f, 46.052f)
        lineTo(44.51f, 46.052f)
        lineTo(19.841f, 89.068f)
        lineTo(16.387f, 95.199f)
        lineTo(21.321f, 103.802f)
        lineTo(21.321f, 104f)
        lineTo(36.616f, 104f)
        close()
    },
    Path().apply {
        moveTo(77.862f, 103.703f)
        lineTo(92.959f, 103.703f)
        lineTo(112.694f, 69.29f)
        lineTo(116.148f, 63.357f)
        lineTo(111.214f, 54.754f)
        lineTo(111.214f, 54.556f)
        lineTo(96.018f, 54.556f)
        lineTo(86.151f, 71.762f)
        lineTo(85.46f, 73.048f)
        lineTo(75.592f, 55.842f)
        lineTo(74.902f, 54.556f)
        lineTo(65.034f, 71.762f)
        lineTo(62.272f, 76.509f)
        lineTo(77.073f, 102.318f)
        close()
    }
)

private const val LOGO_BOUNDS_LEFT = 8f
private const val LOGO_BOUNDS_TOP = 24f
private const val LOGO_BOUNDS_RIGHT = 116.148f
private const val LOGO_BOUNDS_BOTTOM = 104f
private const val LOGO_BOUNDS_WIDTH = LOGO_BOUNDS_RIGHT - LOGO_BOUNDS_LEFT
private const val LOGO_BOUNDS_HEIGHT = LOGO_BOUNDS_BOTTOM - LOGO_BOUNDS_TOP
private const val LOGO_BOUNDS_CENTER_X = (LOGO_BOUNDS_LEFT + LOGO_BOUNDS_RIGHT) / 2f
private const val LOGO_BOUNDS_CENTER_Y = (LOGO_BOUNDS_TOP + LOGO_BOUNDS_BOTTOM) / 2f
