package com.example.chatterinomobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.theme.Twick

@Composable
internal fun ConnectTwitchScreen(
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val backSwipeThresholdPx = with(density) { 72.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onBack) {
                var startedAtEdge = false
                var dragDistance = 0f

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startedAtEdge = offset.x <= edgeWidthPx
                        dragDistance = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (startedAtEdge) {
                            dragDistance += dragAmount
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (startedAtEdge && dragDistance > backSwipeThresholdPx) {
                            onBack()
                        }
                        startedAtEdge = false
                        dragDistance = 0f
                    },
                    onDragCancel = {
                        startedAtEdge = false
                        dragDistance = 0f
                    }
                )
            }
            .background(Twick.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp)
        ) {
            OnboardingAppBar(onBack = onBack, showBack = false)

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = "Connect an account",
                    color = Twick.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.52).sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "7TV Mobile never stores your credentials on our servers. " +
                            "You'll authenticate directly with Twitch and we keep the token on-device.",
                    color = Twick.Ink3,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(28.dp))

                TwitchAccountCard()

                Spacer(Modifier.height(20.dp))

                ScopeNote()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TwitchPrimaryButton(
                    text = "Connect with Twitch",
                    onClick = onConnect
                )
                if (onSkip != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Skip and connect later",
                        color = Twick.Ink3,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TwitchAccountCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Twick.AccentSoft)
            .border(1.dp, Twick.Accent, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TwitchLogo(size = 36.dp)

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Twitch",
                color = Twick.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Log in with OAuth · chat + VODs + clips",
                color = Twick.Ink3,
                fontSize = 11.sp
            )
        }

        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Twick.Accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ScopeNote() {
    val scopes = listOf(
        "Read your follows",
        "Read and send chat messages",
        "Read user profile info"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Twick.S1)
            .border(1.dp, Twick.Hairline, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "THIS WILL ALLOW 7TV TO",
            color = Twick.Ink3,
            fontSize = 11.sp,
            letterSpacing = 0.66.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            scopes.forEach { scope ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Twick.Success,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = scope,
                        color = Twick.Ink,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
