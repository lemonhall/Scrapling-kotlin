package io.github.d4vinci.scrapling.spiders

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulerTest {
    @Test
    fun schedulerStartsEmpty() {
        val scheduler = Scheduler()

        assertTrue(scheduler.isEmpty)
        assertEquals(0, scheduler.size())
    }

    @Test
    fun schedulerPrioritizesHigherPriorityAndDeduplicates() = runTest {
        val scheduler = Scheduler()
        val low = Request("https://example.com/low", priority = 1)
        val high = Request("https://example.com/high", priority = 10)
        val duplicate = Request("https://example.com/high", priority = 5)

        assertTrue(scheduler.enqueue(low))
        assertTrue(scheduler.enqueue(high))
        assertTrue(!scheduler.enqueue(duplicate))
        assertEquals(2, scheduler.size())
        assertEquals("https://example.com/high", scheduler.dequeue().url)
        assertEquals("https://example.com/low", scheduler.dequeue().url)
        assertTrue(scheduler.isEmpty)
    }

    @Test
    fun schedulerSnapshotAndRestorePreserveQueueOrder() = runTest {
        val scheduler = Scheduler()
        scheduler.enqueue(Request("https://example.com/1", priority = 10))
        scheduler.enqueue(Request("https://example.com/2", priority = 1))

        val (requests, seen) = scheduler.snapshot()
        val restored = Scheduler()
        restored.restore(CheckpointData(requests = requests, seen = seen))

        assertEquals(2, restored.size())
        assertEquals("https://example.com/1", restored.dequeue().url)
        assertEquals("https://example.com/2", restored.dequeue().url)
    }
}
