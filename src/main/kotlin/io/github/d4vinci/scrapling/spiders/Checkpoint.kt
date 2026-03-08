package io.github.d4vinci.scrapling.spiders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class CheckpointData(
    val requests: List<Request> = emptyList(),
    val seen: Set<String> = emptySet(),
) : Serializable

class CheckpointManager(
    crawldir: String,
    val interval: Double = 300.0,
) {
    constructor(crawldir: Path, interval: Double = 300.0) : this(crawldir.toString(), interval)

    val crawldir: Path = Path.of(crawldir)
    internal val checkpointPath: Path = this.crawldir.resolve(CHECKPOINT_FILE)

    init {
        require(interval >= 0) { "Checkpoints interval must be equal or greater than 0." }
    }

    suspend fun hasCheckpoint(): Boolean = withContext(Dispatchers.IO) {
        checkpointPath.exists()
    }

    suspend fun save(data: CheckpointData) = withContext(Dispatchers.IO) {
        crawldir.createDirectories()
        val tempPath = checkpointPath.resolveSibling("${checkpointPath.fileName}.tmp")
        runCatching {
            tempPath.outputStream().buffered().use { output ->
                java.io.ObjectOutputStream(output).use { stream ->
                    stream.writeObject(data)
                }
            }
            java.nio.file.Files.move(
                tempPath,
                checkpointPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.onFailure { throwable ->
            tempPath.deleteIfExists()
            throw throwable
        }
    }

    suspend fun load(): CheckpointData? = withContext(Dispatchers.IO) {
        if (!checkpointPath.exists()) {
            return@withContext null
        }
        runCatching {
            checkpointPath.inputStream().buffered().use { input ->
                java.io.ObjectInputStream(input).use { stream ->
                    stream.readObject() as CheckpointData
                }
            }
        }.getOrNull()
    }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        checkpointPath.deleteIfExists()
    }

    companion object {
        const val CHECKPOINT_FILE: String = "checkpoint.pkl"
    }
}
