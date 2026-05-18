package com.example.chatterinomobile.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import com.example.chatterinomobile.R

@Composable
internal fun TwitchLogo(
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_twitch_logo),
        contentDescription = "Twitch",
        modifier = modifier.size(size),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    )
}
