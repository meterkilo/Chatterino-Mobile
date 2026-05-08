package com.example.chatterinomobile.data.repository

import android.util.Log
import com.example.chatterinomobile.data.local.PaintDiskCache
import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.remote.api.SevenTvCosmeticsApi
import com.example.chatterinomobile.data.remote.mapper.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PaintRepositoryImpl(
    private val sevenTvCosmeticsApi: SevenTvCosmeticsApi,
    private val diskCache: PaintDiskCache
) : PaintRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _paintAssignments = MutableSharedFlow<PaintAssignment>(
        replay = 0,
        extraBufferCapacity = 256
    )
    override val paintAssignments: SharedFlow<PaintAssignment> = _paintAssignments.asSharedFlow()

    @Volatile
    private var paintByUserId: Map<String, Paint> = emptyMap()
    private val cacheMutex = Mutex()
    private val attemptedUsers = HashSet<String>()
    private val inFlightUsers = HashSet<String>()

    init {
        scope.launch {
            diskCache.read()?.let { snapshot ->
                cacheMutex.withLock {
                    paintByUserId = paintByUserId + snapshot.paintsByUserId
                }
                for ((userId, paint) in snapshot.paintsByUserId) {
                    _paintAssignments.tryEmit(PaintAssignment(userId, paint))
                }
            }
        }
    }

    override fun requestPaintForUser(twitchUserId: String) {
        if (twitchUserId.isBlank()) return
        if (paintByUserId.containsKey(twitchUserId)) return
        scope.launch { fetchPaintForUser(twitchUserId) }
    }

    private suspend fun fetchPaintForUser(twitchUserId: String) {
        val shouldFetch = cacheMutex.withLock {
            if (paintByUserId.containsKey(twitchUserId)) return
            if (twitchUserId in attemptedUsers) return
            if (!inFlightUsers.add(twitchUserId)) return
            true
        }
        if (!shouldFetch) return

        val response = withContext(Dispatchers.IO) {
            runCatching { sevenTvCosmeticsApi.getUserCosmetics(twitchUserId) }
        }

        if (response.isFailure) {
            Log.w(TAG, "paint fetch failed for $twitchUserId", response.exceptionOrNull())
            cacheMutex.withLock { inFlightUsers.remove(twitchUserId) }
            return
        }

        val responseDto = response.getOrNull()
        if (!responseDto?.errors.isNullOrEmpty()) {
            Log.w(TAG, "paint gql errors for $twitchUserId: ${responseDto?.errors?.joinToString { it.message }}")
        }

        val style = responseDto
            ?.data
            ?.users
            ?.userByConnection
            ?.style
        val activePaintDto = style?.activePaint
        val paint = activePaintDto?.toDomain()

        Log.d(
            TAG,
            "user=$twitchUserId activePaintId=${style?.activePaintId} " +
                "hasDto=${activePaintDto != null} mappedTo=${paint?.javaClass?.simpleName}"
        )

        cacheMutex.withLock {
            inFlightUsers.remove(twitchUserId)
            attemptedUsers.add(twitchUserId)
            if (paint != null) {
                paintByUserId = paintByUserId + mapOf(twitchUserId to paint)
            }
        }

        if (paint != null) {
            val emitted = _paintAssignments.tryEmit(PaintAssignment(twitchUserId, paint))
            Log.d(TAG, "emitted=$emitted user=$twitchUserId paint=$paint")
            scope.launch { diskCache.patch(twitchUserId, paint) }
        }
    }

    override fun findPaintForUser(twitchUserId: String): Paint? =
        paintByUserId[twitchUserId]

    override fun snapshot(): Map<String, Paint> = paintByUserId

    companion object {
        private const val TAG = "PaintRepository"
    }
}
