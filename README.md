# 7TV Mobile

An Android Twitch client focused on stream playback, chat, and third-party emote support.

The goal is to make Twitch chat feel less constrained on mobile while keeping the app native, responsive, and straightforward to work on. The chat behavior takes cues from desktop clients like Chatterino, especially around recent messages, replies, badges, emotes, and compact message rendering.

## Features

- Watch Twitch livestreams with player controls, quality selection, fullscreen, theater mode, and picture-in-picture.
- Connect to Twitch IRC for live chat, moderation events, replies, and recent messages on join.
- Render Twitch, 7TV, BTTV, and FFZ emotes.
- Browse live channels and categories.
- Sign in with Twitch OAuth, with anonymous mode when auth is not configured.
- Cache emotes, badges, paints, follow lists, and discovery snapshots to avoid unnecessary startup work.

## Project Status

This is actively being built and refactored. The core pieces are in place, but player edge cases, chat layout behavior, caching, and discovery performance are still being tuned.

## Tech

- Kotlin
- Jetpack Compose
- Media3 / ExoPlayer
- Ktor + OkHttp
- Kotlin Coroutines / Flow
- Koin
- Coil

## Setup

Use the Gradle wrapper included in the repo.

Expected local setup:

- Android Studio
- Gradle JVM compatible with Java 21
- Android SDK 36

Add a Twitch client ID to `local.properties` for authenticated Twitch features:

```properties
twitchClientId=YOUR_CLIENT_ID
```

The app uses this redirect URI for Twitch login:

```text
chatterinomobile://oauth/twitch
```

Then build from Android Studio or run:

```bash
./gradlew :app:assembleDebug
```

## Notes

This project references behavior from Chatterino and Android Twitch clients like Xtra where it makes sense, but the implementation is native Android rather than a port.
