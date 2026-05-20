package com.example.chatterinomobile

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.ui.auth.AuthViewModel
import com.example.chatterinomobile.ui.auth.TwitchAuthLauncher
import com.example.chatterinomobile.ui.channels.ChannelTabsViewModel
import com.example.chatterinomobile.ui.chat.ChatRoute
import com.example.chatterinomobile.ui.chat.ChatViewModel
import com.example.chatterinomobile.ui.discovery.DiscoveryScreen
import com.example.chatterinomobile.ui.onboarding.OnboardingFlow
import com.example.chatterinomobile.ui.player.StreamPlayerViewModel
import com.example.chatterinomobile.ui.player.TwitchStreamStage
import com.example.chatterinomobile.ui.settings.SettingsViewModel
import com.example.chatterinomobile.ui.theme.ChatterinoMobileTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModel()
    private val tabsViewModel: ChannelTabsViewModel by viewModel()
    private val chatViewModel: ChatViewModel by viewModel()
    private val streamPlayerViewModel: StreamPlayerViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    private var playerOnlyPipMode by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        val url = data.toString()
        if (url.startsWith(AuthRepository.REDIRECT_URI)) {
            authViewModel.onRedirectIntercepted(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleAuthRedirect(intent)

        setContent {
            val authState by authViewModel.uiState.collectAsState()
            val activeChannel by tabsViewModel.activeChannel.collectAsState()
            val joinedChannels by tabsViewModel.joinedChannels.collectAsState()

            var onboardingComplete by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(authState.isLoggedIn, authState.isLoading) {
                if (authState.isLoggedIn && !authState.isLoading && !onboardingComplete) {
                    if (savedInstanceState == null) onboardingComplete = true
                }
                if (!authState.isLoggedIn && !authState.isLoading && onboardingComplete) {
                    onboardingComplete = false
                }
            }

            LaunchedEffect(activeChannel.channelLogin, activeChannel.hydration?.channelId) {
                chatViewModel.setActiveChannel(
                    channelLogin = activeChannel.channelLogin,
                    channelId = activeChannel.hydration?.channelId
                )
            }

            val activity = LocalActivity.current
            LaunchedEffect(authState.authorizeUrl) {
                val url = authState.authorizeUrl ?: return@LaunchedEffect
                val ctx = activity ?: return@LaunchedEffect
                TwitchAuthLauncher.launch(ctx, url)
                authViewModel.onAuthorizeUrlConsumed()
            }

            val navController = rememberNavController()

            LaunchedEffect(activeChannel.channelLogin) {
                if (activeChannel.channelLogin != null) {
                    navController.navigate(Routes.Chat) {
                        launchSingleTop = true
                    }
                }
            }

            ChatterinoMobileTheme {
                when {
                    playerOnlyPipMode && activeChannel.channelLogin != null -> TwitchStreamStage(
                        activeChannel = activeChannel,
                        playerViewModel = streamPlayerViewModel,
                        videoVisible = true,
                        fillBounds = true,
                        pipMode = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    !onboardingComplete -> OnboardingFlow(
                        isLoading = authState.isLoading,
                        isLoggedIn = authState.isLoggedIn,
                        onConnectTwitch = authViewModel::startLogin,
                        onFinish = { onboardingComplete = true }
                    )

                    else -> Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Routes.Discovery,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Routes.Discovery) {
                                DiscoveryScreen(
                                    pinnedChannelLogins = joinedChannels,
                                    onJoinChannel = tabsViewModel::joinChannel,
                                    onRemovePin = tabsViewModel::leaveChannel,
                                    onLogout = authViewModel::logout,
                                    onClearCache = settingsViewModel::clearCache
                                )
                            }
                            composable(Routes.Chat) {
                                ChatRoute(
                                    chatViewModel = chatViewModel,
                                    tabsViewModel = tabsViewModel,
                                    streamPlayerViewModel = streamPlayerViewModel,
                                    isLoggedIn = authState.isLoggedIn,
                                    authUserId = authState.userId,
                                    authLogin = authState.login,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun enterPlayerPip(sourceRectHint: Rect?) {
        playerOnlyPipMode = true
        window.decorView.post {
            if (!enterPip(sourceRectHint)) {
                playerOnlyPipMode = false
            }
        }
    }

    private fun enterPip(sourceRectHint: Rect?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply {
                sourceRectHint?.let { setSourceRectHint(it) }
            }
            .build()
        return runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerOnlyPipMode = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        TwitchAuthLauncher.bind(this)
    }

    override fun onStop() {
        TwitchAuthLauncher.unbind(this)
        super.onStop()
    }
}

private object Routes {
    const val Discovery = "discovery"
    const val Chat = "chat"
}
