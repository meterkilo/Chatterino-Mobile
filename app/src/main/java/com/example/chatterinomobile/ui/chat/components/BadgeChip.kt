package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chatterinomobile.data.model.Badge

@Composable
fun BadgeChip(
    badge: Badge,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = badge.imageURL,
        contentDescription = badge.description,
        modifier = modifier
            .size(size)
            .then(
                if (badge.backgroundColor != null) {
                    Modifier.background(Color(badge.backgroundColor), RoundedCornerShape(2.dp))
                } else {
                    Modifier
                }
            ),
    )
}
