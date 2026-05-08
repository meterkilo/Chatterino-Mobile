package com.example.chatterinomobile.data.local

import com.example.chatterinomobile.data.model.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class PaintDiskCache(private val root: DiskCacheRoot) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    suspend fun read(): Snapshot? = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = paintsFile()
            if (!file.exists()) return@withLock null
            runCatching {
                json.decodeFromString(SnapshotDto.serializer(), file.readText())
            }.map { Snapshot(it.savedAtEpochMillis, it.paintsByUserId) }.getOrNull()
        }
    }

    suspend fun write(paintsByUserId: Map<String, Paint>) = withContext(Dispatchers.IO) {
        lock.withLock {
            writeLocked(Snapshot(System.currentTimeMillis(), paintsByUserId))
        }
    }

    suspend fun patch(twitchUserId: String, paint: Paint) = withContext(Dispatchers.IO) {
        if (twitchUserId.isBlank()) return@withContext
        lock.withLock {
            val current = readLocked() ?: Snapshot(0L, emptyMap())
            val merged = current.paintsByUserId + (twitchUserId to paint)
            writeLocked(Snapshot(System.currentTimeMillis(), merged))
        }
    }

    private fun readLocked(): Snapshot? {
        val file = paintsFile()
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(SnapshotDto.serializer(), file.readText())
        }.map { Snapshot(it.savedAtEpochMillis, it.paintsByUserId) }.getOrNull()
    }

    private fun writeLocked(snapshot: Snapshot) {
        val file = paintsFile()
        val tmp = File(file.parentFile, file.name + ".tmp")
        val payload = json.encodeToString(
            SnapshotDto.serializer(),
            SnapshotDto(snapshot.savedAtEpochMillis, snapshot.paintsByUserId)
        )
        tmp.writeText(payload)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun paintsFile(): File = File(root.paintDir(), "users.json")

    data class Snapshot(
        val savedAtEpochMillis: Long,
        val paintsByUserId: Map<String, Paint>
    )

    @Serializable
    private data class SnapshotDto(
        val savedAtEpochMillis: Long,
        val paintsByUserId: Map<String, Paint>
    )
}
