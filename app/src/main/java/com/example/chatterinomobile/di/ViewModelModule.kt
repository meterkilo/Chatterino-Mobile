package com.example.chatterinomobile.di

import com.example.chatterinomobile.ui.auth.AuthViewModel
import com.example.chatterinomobile.ui.channels.ChannelTabsViewModel
import com.example.chatterinomobile.ui.chat.ChatViewModel
import com.example.chatterinomobile.ui.discovery.DiscoveryViewModel
import com.example.chatterinomobile.ui.player.StreamPlayerViewModel
import com.example.chatterinomobile.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { ChannelTabsViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { StreamPlayerViewModel(androidApplication(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { DiscoveryViewModel(get(), get(), get(), get()) }
}
