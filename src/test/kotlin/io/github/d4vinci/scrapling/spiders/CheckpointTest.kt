package io.github.d4vinci.scrapling.spiders

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckpointTest {
    @Test
    fun checkpointDataDefaultsAreEmpty() {
        val data = CheckpointData()

        assertEquals(emptyList(), data.requests)
        assertEquals(emptySet(), data.seen)
    }

    @Test
    fun checkpointManagerValidatesInterval() {
        assertFailsWith<IllegalArgumentException> {
            CheckpointManager("/tmp/test", interval = -1.0)
        }
    }

    @Test
    fun checkpointPathUsesCanonicalFilename() {
        val manager = CheckpointManager("/tmp/test-crawl")

        assertTrue(manager.checkpointPath.toString().endsWith("checkpoint.pkl"))
    }

    @Test
    fun checkpointSaveLoadAndCleanupWork() = runTest {
        val dir = Files.createTempDirectory("scrapling-checkpoint")
        val manager = CheckpointManager(dir)
        val data = CheckpointData(
            requests = listOf(Request("https://example.com", priority = 2)),
            seen = setOf("fp-1", "fp-2"),
        )

        assertTrue(!manager.hasCheckpoint())
        manager.save(data)
        assertTrue(manager.hasCheckpoint())

        val loaded = manager.load()
        assertNotNull(loaded)
        assertEquals("https://example.com", loaded.requests.single().url)
        assertEquals(setOf("fp-1", "fp-2"), loaded.seen)

        manager.cleanup()
        assertTrue(!manager.hasCheckpoint())
        assertNull(manager.load())
    }
}
