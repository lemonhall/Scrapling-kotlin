package io.github.d4vinci.scrapling.core.storage

import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SQLiteStorageSystemTest {
    @Test
    fun sqliteStorageCreationSupportsInMemoryMode() {
        val storage = SQLiteStorageSystem(storageFile = ":memory:")

        assertNotNull(storage)
        storage.close()
    }

    @Test
    fun sqliteStorageWithFileCreatesDatabaseFile() {
        val tempFile = Files.createTempFile("scrapling-kotlin-", ".db")
        tempFile.deleteIfExists()

        val storage = SQLiteStorageSystem(storageFile = tempFile.toString())
        try {
            assertTrue(tempFile.exists())
        } finally {
            storage.close()
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun sqliteStorageKeepsUrlContext() {
        val storage = SQLiteStorageSystem(storageFile = ":memory:", url = "https://example.com")
        try {
            assertEquals("https://example.com", storage.url)
        } finally {
            storage.close()
        }
    }
}
