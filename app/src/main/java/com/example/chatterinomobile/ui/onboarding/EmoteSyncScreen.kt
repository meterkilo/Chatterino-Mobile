package com.example.chatterinomobile.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.theme.PublicSansFontFamily
import com.example.chatterinomobile.ui.theme.Twick
import kotlinx.coroutines.delay

@Composable
internal fun EmoteSyncScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    var status by remember {
        mutableStateOf(
            mapOf(
                "7tv" to ProviderStatus.Syncing,
                "bttv" to ProviderStatus.Syncing,
                "ffz" to ProviderStatus.Syncing
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(900)
        status = status + ("7tv" to ProviderStatus.Synced)
        delay(700)
        status = status + ("bttv" to ProviderStatus.Synced)
        delay(900)
        status = status + ("ffz" to ProviderStatus.Synced)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Twick.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp)
        ) {
            OnboardingAppBar(onBack = onBack, title = "Sync emotes")

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "7TV Mobile fetches emote sets directly from each provider. " +
                            "Cached locally — no server in between.",
                    color = Twick.Ink3,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProviderCard(
                        id = "7TV",
                        name = "7TV",
                        count = "128 global · 412 channel",
                        accent = Color(0xFF1DB954),
                        status = status["7tv"] ?: ProviderStatus.Syncing
                    )
                    ProviderCard(
                        id = "BTTV",
                        name = "BetterTTV",
                        count = "73 global · 210 channel",
                        accent = Color(0xFF3478F6),
                        status = status["bttv"] ?: ProviderStatus.Syncing
                    )
                    ProviderCard(
                        id = "FFZ",
                        name = "FrankerFaceZ",
                        count = "56 global · 180 channel",
                        accent = Color(0xFFE07B2A),
                        status = status["ffz"] ?: ProviderStatus.Syncing
                    )
                }

                Spacer(Modifier.height(20.dp))

                AnimatedEmotesCard()

                Spacer(Modifier.height(28.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            PrimaryButton(
                text = "Finish setup",
                onClick = onFinish
            )
        }
    }
}

private enum class ProviderStatus { Syncing, Synced }

@Composable
private fun ProviderCard(
    id: String,
    name: String,
    count: String,
    accent: Color,
    status: ProviderStatus
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Twick.S1)
            .border(1.dp, Twick.Hairline, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = id,
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PublicSansFontFamily
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Twick.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = count,
                    color = Twick.Ink3,
                    fontSize = 11.sp
                )
            }
            StatusPill(status)
        }

        Spacer(Modifier.height(12.dp))

        EmotePreviewRow()
    }
}

@Composable
private fun StatusPill(status: ProviderStatus) {
    when (status) {
        ProviderStatus.Syncing -> {
            val transition = rememberInfiniteTransition(label = "syncBlink")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "alpha"
            )
            Text(
                text = "SYNCING",
                color = Twick.Accent.copy(alpha = alpha),
                fontSize = 10.sp,
                fontFamily = PublicSansFontFamily,
                letterSpacing = 0.6.sp
            )
        }
        ProviderStatus.Synced -> Text(
            text = "SYNCED",
            color = Twick.Success,
            fontSize = 10.sp,
            fontFamily = PublicSansFontFamily,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun EmotePreviewRow() {
    val codes = listOf("PogU", "Clap", "kekW", "PepeLaugh", "Sadge", "5Head", "KEKW", "OMEGALUL")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        codes.take(8).forEach { code ->
            EmoteChip(code)
        }
    }
}

@Composable
private fun EmoteChip(code: String) {



    val hue = ((code.hashCode() ushr 1) % 360).toFloat()
    val color = Color.hsv(hue, 0.5f, 0.7f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = code.take(2),
            color = color,
            fontSize = 8.sp,
            fontFamily = PublicSansFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedEmotesCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Twick.S1)
            .border(1.dp, Twick.Hairline, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Twick.Accent,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Animated emotes",
                color = Twick.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "APNG and WebP animations. Disable in Settings → Data if you want to save bandwidth.",
                color = Twick.Ink3,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        ToggleOn()
    }
}

@Composable
private fun ToggleOn() {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Twick.Accent),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .padding(end = 2.dp)
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        )
    }
}
