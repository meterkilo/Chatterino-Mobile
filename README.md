# 7TV Mobile / Twick Android Rewrite

A native Android Twitch viewer client with integrated live streaming, built for low-latency chat and polished UX. Inspired by **Chatterino** and **Chatterino7**, built with **Kotlin** and **Jetpack Compose**.

## Features

### Chat & Streaming
- **Native ExoPlayer HLS streaming** with ad support (plays through Twitch ads, no ad-free fallback)
- **Low-latency Twitch chat** via WebSocket with auto-reconnect and emote rendering
- **Quality selection** with dynamic m3u8-sourced quality labels
- **Theater mode** (horizontal split) and vertical layouts with resizable dividers
- **Chat moderation:** badges, verified indicator, reply threads, deletion state
- **Emote support:** 7TV, BTTV, FFZ emotes with inline rendering

### UI/UX
- **7TV Mobile branding** with holographic shimmer logo and refined wordmark
- **Bottom navigation bar** with gradient fade into solid black (Home, Browse, You)
- **Player overlay controls** (Settings/Quality, Mute, Theater, Fullscreen) with top/bottom gradients
- **Back-to-restore button** for exiting the player (diagonal NorthWest icon)
- **Compact chat messages** with username:message format (no extra spacing)
- **Responsive layouts** (portrait, theater, chat-fullscreen) with smooth divider resizing

### Authentication
- **Twitch OAuth device code flow** with custom URI scheme redirect
- **Session persistence** via encrypted shared preferences
- **Automatic token refresh** with fallback to re-auth

## Tech Stack

| Component | Technology |
|-----------|------------|
| **UI Framework** | Jetpack Compose + Material3 |
| **Video Playback** | Media3 (ExoPlayer 1.10) with HLS |
| **Chat & Networking** | Ktor HTTP client + WebSocket, OkHttp |
| **Reactive** | Kotlin Coroutines + Flow (StateFlow, SharedFlow) |
| **Serialization** | kotlinx.serialization (JSON) |
| **Dependency Injection** | Koin |
| **Image Loading** | Coil (async image composables) |
| **Storage** | Android SharedPreferences (encrypted) |
| **Minimum API** | 28 (Android 9) |

## Architecture

### Layers
- **UI** (`ui/`) — Jetpack Compose screens and components (player, chat, discovery, onboarding)
- **Data** (`data/`) — Repository pattern, API clients, local state, models
- **Dependency Injection** — Koin modules for viewmodels, repositories, clients

### Key Viewmodels
- `StreamPlayerViewModel` — ExoPlayer setup, quality selection, mute state, playback lifecycle
- `ChatViewModel` — WebSocket chat flow, message rendering, moderation state
- `AuthViewModel` — OAuth device code flow, token management
- `ChannelTabsViewModel` — Active channel state, joined channels list

## Building & Running

### Prerequisites
- **Android Studio** 2024.1+
- **JDK** 17+
- **Gradle** 8.3+

### Setup
1. Clone the repo
2. Add your Twitch Developer Console **Client ID** to `local.properties`:
   ```properties
   twitchClientId=YOUR_CLIENT_ID_HERE
   ```
3. Ensure OAuth redirect URI is registered in Twitch Dev Console:
   ```
   chatterinomobile://oauth/twitch
   ```
4. Open in Android Studio and sync Gradle
5. Run on emulator (API 28+) or physical device

### Build Variants
- **Debug** — Full logging, R8 disabled, debuggable
- **Release** — Proguard/R8 minification, optimized

## Known Limitations

- **Kick support** — Chat client structure supports Kick but streaming is Twitch-only
- **Kotlin Multiplatform** — Currently Android-only; KMP architecture is a future goal
- **Offline channels** — Player is hidden (shows poster with "Offline" label)
- **VoD playback** — Not supported (live-only)

## Roadmap

- [ ] Channel points / bit alerts
- [ ] Custom chat colors & user card UI
- [ ] Settings screen (volume, layout defaults, theme)
- [ ] iOS port via Kotlin Multiplatform

## Credits

Inspired by:
- [Chatterino](https://github.com/Chatterino/chatterino2) — cross-platform Twitch chat
- [Chatterino7](https://github.com/SevenTV/chatterino7) — SevenTV integrations
- [Xtra](https://github.com/An2nDev/Xtra), [Twire](https://github.com/Twire-tv/Twire), [Frosty](https://github.com/mochadwi/frosty) — Android Twitch client optimizations
