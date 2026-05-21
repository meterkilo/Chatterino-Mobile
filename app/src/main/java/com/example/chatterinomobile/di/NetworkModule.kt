package com.example.chatterinomobile.di

import com.example.chatterinomobile.data.remote.api.BttvApi
import com.example.chatterinomobile.data.remote.api.ChatterinoApi
import com.example.chatterinomobile.data.remote.api.FfzApi
import com.example.chatterinomobile.data.remote.api.RecentMessagesApi
import com.example.chatterinomobile.data.remote.api.SevenTvApi
import com.example.chatterinomobile.data.remote.api.SevenTvCosmeticsApi
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.remote.api.TwitchOAuthApi
import com.example.chatterinomobile.data.remote.api.TwitchPlaybackApi
import com.example.chatterinomobile.data.remote.irc.IrcMessageMapper
import com.example.chatterinomobile.data.remote.irc.MessageEnricher
import com.example.chatterinomobile.data.remote.irc.ModerationEventMapper
import com.example.chatterinomobile.data.remote.irc.RoomStateMapper
import com.example.chatterinomobile.data.remote.irc.TwitchIrcClient
import com.example.chatterinomobile.data.remote.irc.UserStateMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }

            install(WebSockets)
        }
    }

    single { SevenTvApi(get()) }
    single { BttvApi(get()) }
    single { FfzApi(get()) }
    single { ChatterinoApi(get()) }
    single { RecentMessagesApi(get()) }

    single { SevenTvCosmeticsApi(get()) }
    single { TwitchOAuthApi(get()) }
    single { TwitchHelixApi(get(), get()) }
    single { TwitchPlaybackApi(get()) }

    single { TwitchIrcClient(get(), get()) }
    single { IrcMessageMapper(get()) }
    single { ModerationEventMapper() }
    single { RoomStateMapper() }
    single { UserStateMapper(get()) }
    single { MessageEnricher(get(), get(), get()) }
}
