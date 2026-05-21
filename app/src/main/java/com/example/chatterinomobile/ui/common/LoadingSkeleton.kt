package com.example.chatterinomobile.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.chatterinomobile.ui.theme.Twick

@Composable
fun rememberSkeletonBrush(alpha: Float = 1f): Brush {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShimmerOffset"
    )
    val start = shimmer * 980f - 420f
    return Brush.linearGradient(
        colors = listOf(
            Twick.S2.copy(alpha = 0.70f * alpha),
            Twick.S3.copy(alpha = 0.96f * alpha),
            Twick.S2.copy(alpha = 0.70f * alpha)
        ),
        start = Offset(start, 0f),
        end = Offset(start + 420f, 420f)
    )
}

fun Modifier.skeleton(
    brush: Brush,
    shape: Shape = RoundedCornerShape(6.dp)
): Modifier = clip(shape).background(brush)

@Composable
fun SkeletonBox(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    Box(modifier = modifier.skeleton(brush, shape))
}
