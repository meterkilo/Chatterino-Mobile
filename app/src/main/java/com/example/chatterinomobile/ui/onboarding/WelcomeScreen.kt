package com.example.chatterinomobile.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.theme.Twick

@Composable
internal fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Twick.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 140.dp)
        ) {
            BrandMark()

            Spacer(Modifier.height(23.dp))

            Text(
                text = headline(),
                color = Twick.Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.02).sp,
                lineHeight = 36.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "A lightweight viewing client for Twitch & Kick with native 7TV, " +
                        "BetterTTV and FrankerFaceZ emote support.",
                color = Twick.Ink2,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(36.dp))

            ValueProps()
        }


        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            PrimaryButton(text = "Get started", onClick = onGetStarted)
        }
    }
}

private fun headline(): AnnotatedString = buildAnnotatedString {
    append("Every emote.\nEvery stream.\n")
    withStyle(SpanStyle(color = OnboardingAccent)) { append("Half the battery.") }
}

@Composable
private fun ValueProps() {
    val props = listOf(
        ValueProp(Icons.Default.AutoAwesome, "Third-party emotes", "7TV · BTTV · FFZ — global & per-channel"),
        ValueProp(Icons.Default.Bolt, "Low-latency", "Fast and responsive chat experience with validated message sending"),
        ValueProp(Icons.Default.Visibility, "Chat-only playback", "Follow the conversation with no video"),
        ValueProp(Icons.Default.Shield, "No trackers", "Open-source, no analytics, no personal data collected")
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        props.forEach { ValuePropRow(it) }
    }
}

private data class ValueProp(val icon: ImageVector, val title: String, val description: String)

@Composable
private fun ValuePropRow(prop: ValueProp) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Twick.S2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = prop.icon,
                contentDescription = null,
                tint = Twick.Accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(prop.title, color = Twick.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(prop.description, color = Twick.Ink3, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
