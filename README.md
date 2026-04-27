# Twick Android Rewrite

A mobile Twitch and Kick chat client inspired by **Chatterino** and **Chatterino7**. Built with Kotlin and Jetpack Compose, with an eye toward Kotlin Multiplatform (KMP) down the road.

### Tech stack
* **UI:** Jetpack Compose
* **Async:** Coroutines + Flow
* **Networking:** Ktor (OkHttp engine)
* **Serialization:** kotlinx.serialization
* **DI:** Koin
* **Target:** MVVM architecture. The data/domain layers are pure Kotlin (no Android imports) so I can hopefully share them with an iOS client later.


