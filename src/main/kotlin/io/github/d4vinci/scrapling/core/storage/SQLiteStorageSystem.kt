package io.github.d4vinci.scrapling.core.storage

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json

class SQLiteStorageSystem(
    val storageFile: String,
    override val url: String? = null,
) : StorageSystem {
    private val connection: Connection

    init {
        if (storageFile != ":memory:") {
            Path.of(storageFile).parent?.let { Files.createDirectories(it) }
        }

        connection = DriverManager.getConnection(
            if (storageFile == ":memory:") "jdbc:sqlite::memory:" else "jdbc:sqlite:$storageFile",
        )
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS storage (
                    id INTEGER PRIMARY KEY,
                    url TEXT,
                    identifier TEXT,
                    element_data TEXT,
                    UNIQUE (url, identifier)
                )
                """.trimIndent(),
            )
        }
    }

    override fun save(element: ElementSnapshot, identifier: String) {
        val sql = "INSERT OR REPLACE INTO storage (url, identifier, element_data) VALUES (?, ?, ?)"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, baseUrl())
            statement.setString(2, identifier)
            statement.setString(3, Json.encodeToString(ElementSnapshot.serializer(), element))
            statement.executeUpdate()
        }
    }

    override fun retrieve(identifier: String): ElementSnapshot? {
        val sql = "SELECT element_data FROM storage WHERE url = ? AND identifier = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, baseUrl())
            statement.setString(2, identifier)
            statement.executeQuery().use { resultSet ->
                return if (resultSet.next()) {
                    Json.decodeFromString(ElementSnapshot.serializer(), resultSet.getString(1))
                } else {
                    null
                }
            }
        }
    }

    override fun close() {
        connection.close()
    }

    private fun baseUrl(): String =
        url?.lowercase()?.let { candidate ->
            runCatching {
                val host = URI(candidate).host
                if (host.isNullOrBlank()) candidate else host.lowercase()
            }.getOrElse { candidate }
        } ?: "default"

    companion object {
        private val cache = ConcurrentHashMap<String, SQLiteStorageSystem>()

        fun cached(storageFile: String, url: String? = null): SQLiteStorageSystem {
            val key = "$storageFile|${url?.lowercase() ?: "default"}"
            return cache.computeIfAbsent(key) { SQLiteStorageSystem(storageFile = storageFile, url = url) }
        }

        fun default(url: String? = null): SQLiteStorageSystem {
            val storagePath = Path.of(System.getProperty("user.dir"), "build", "scrapling-kotlin", "adaptive.db")
            return cached(storagePath.toString(), url)
        }
    }
}
