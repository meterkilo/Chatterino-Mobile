package com.example.chatterinomobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.R

val PublicSansFontFamily = FontFamily(
    Font(R.font.public_sans_regular, FontWeight.Normal),
    Font(R.font.public_sans_medium, FontWeight.Medium),
    Font(R.font.public_sans_semibold, FontWeight.SemiBold),
    Font(R.font.public_sans_bold, FontWeight.Bold)
)

private val BaseTypography = Typography()

private fun TextStyle.withPublicSans() = copy(fontFamily = PublicSansFontFamily)

val Typography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.withPublicSans(),
    displayMedium = BaseTypography.displayMedium.withPublicSans(),
    displaySmall = BaseTypography.displaySmall.withPublicSans(),
    headlineLarge = BaseTypography.headlineLarge.withPublicSans(),
    headlineMedium = BaseTypography.headlineMedium.withPublicSans(),
    headlineSmall = BaseTypography.headlineSmall.withPublicSans(),
    titleLarge = BaseTypography.titleLarge.withPublicSans(),
    titleMedium = BaseTypography.titleMedium.withPublicSans(),
    titleSmall = BaseTypography.titleSmall.withPublicSans(),
    bodyLarge = TextStyle(
        fontFamily = PublicSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.withPublicSans(),
    bodySmall = BaseTypography.bodySmall.withPublicSans(),
    labelLarge = BaseTypography.labelLarge.withPublicSans(),
    labelMedium = BaseTypography.labelMedium.withPublicSans(),
    labelSmall = BaseTypography.labelSmall.withPublicSans()
)
