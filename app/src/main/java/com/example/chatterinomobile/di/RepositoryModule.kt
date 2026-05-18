package com.example.chatterinomobile.di

import com.example.chatterinomobile.BuildConfig
import com.example.chatterinomobile.data.local.BadgeDiskCache
import com.example.chatterinomobile.data.local.DiscoverySnapshotCache
import com.example.chatterinomobile.data.local.DiskCacheRoot
import com.example.chatterinomobile.data.local.EmoteDimensionStore
import com.example.chatterinomobile.data.local.EmoteDiskCache
import com.example.chatterinomobile.data.local.FollowListCache
import com.example.chatterinomobile.data.local.PaintDiskCache
import com.example.chatterinomobile.data.local.PinnedChannelsStore
import com.example.chatterinomobile.data.local.TokenStore
import com.example.chatterinomobile.data.repository.AnonymousAuthRepository
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.data.repository.BadgeRepository
import com.example.chatterinomobile.data.repository.BadgeRepositoryImpl
import com.example.chatterinomobile.data.repository.CacheAdmin
import com.example.chatterinomobile.data.repository.ChannelRepository
import com.example.chatterinomobile.data.repository.ChannelRepositoryImpl
import com.example.chatterinomobile.data.repository.ChatRepository
import com.example.chatterinomobile.data.repository.ChatRepositoryImpl
import com.example.chatterinomobile.data.repository.EmoteRepository
import com.example.chatterinomobile.data.repository.EmoteRepositoryImpl
import com.example.chatterinomobile.data.repository.PaintRepository
import com.example.chatterinomobile.data.repository.PaintRepositoryImpl
import com.example.chatterinomobile.data.repository.TwitchOAuthRepository
import com.example.chatterinomobile.data.repository.TwitchPlaybackRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    single { TokenStore(get()) }

    single { DiskCacheRoot(get()) }
    single { EmoteDiskCache(get()) }
    single { BadgeDiskCache(get()) }
    single { PaintDiskCache(get()) }
    single { FollowListCache(get()) }
    single { DiscoverySnapshotCache(get()) }
    single { PinnedChannelsStore(get()) }
    single { EmoteDimensionStore(get()) }

    single {
        if (BuildConfig.TWITCH_CLIENT_ID.isBlank()) {
            AnonymousAuthRepository(BuildConfig.TWITCH_CLIENT_ID)
        } else {
            TwitchOAuthRepository(get(), get())
        }
    } bind AuthRepository::class

    single {
        EmoteRepositoryImpl(
            sevenTvApi = get(),
            bttvApi = get(),
            ffzApi = get(),
            helixApi = get(),
            diskCache = get(),
            dimensionStore = get()
        )
    } bind EmoteRepository::class
    single {
        BadgeRepositoryImpl(
            helixApi = get(),
            sevenTvCosmeticsApi = get(),
            ffzApi = get(),
            chatterinoApi = get(),
            diskCache = get()
        )
    } bind BadgeRepository::class
    single { PaintRepositoryImpl(get(), get()) } bind PaintRepository::class
    single { ChannelRepositoryImpl(get()) } bind ChannelRepository::class
    single { TwitchPlaybackRepository(get()) }

    single { CacheAdmin(get(), get(), get(), get(), get()) }

    single {
        ChatRepositoryImpl(
            ircClient = get(),
            mapper = get(),
            moderationMapper = get(),
            roomStateMapper = get(),
            userStateMapper = get(),
            enricher = get(),
            recentMessagesApi = get(),
            channelRepository = get(),
            badgeRepository = get(),
            emoteRepository = get()
        )
    } bind ChatRepository::class
}
