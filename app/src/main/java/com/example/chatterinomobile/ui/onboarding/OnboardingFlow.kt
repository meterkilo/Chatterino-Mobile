package com.example.chatterinomobile.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun OnboardingFlow(
    isLoading: Boolean,
    isLoggedIn: Boolean,
    onConnectTwitch: () -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Splash) }
    val currentIsLoggedIn by rememberUpdatedState(isLoggedIn)




    LaunchedEffect(Unit) {
        if (step == OnboardingStep.Splash) {
            delay(1200)


            step = if (currentIsLoggedIn) OnboardingStep.EmoteSync else OnboardingStep.Welcome
        }
    }





    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && step == OnboardingStep.ConnectTwitch) {
            step = OnboardingStep.EmoteSync
        }
    }

    if (isLoading && step != OnboardingStep.Splash) {
        SplashScreen(showWordmark = false)
        return
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> direction * full } + fadeIn(tween(220)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { full -> -direction * full } + fadeOut(tween(180))
                )
        },
        label = "onboarding"
    ) { current ->
        when (current) {
            OnboardingStep.Splash -> SplashScreen(showWordmark = true)
            OnboardingStep.Welcome -> WelcomeScreen(
                onGetStarted = { step = OnboardingStep.ConnectTwitch }
            )
            OnboardingStep.ConnectTwitch -> ConnectTwitchScreen(
                onBack = { step = OnboardingStep.Welcome },
                onConnect = onConnectTwitch
            )
            OnboardingStep.EmoteSync -> EmoteSyncScreen(
                onBack = { step = OnboardingStep.ConnectTwitch },
                onFinish = onFinish
            )
        }
    }
}

enum class OnboardingStep { Splash, Welcome, ConnectTwitch, EmoteSync }
