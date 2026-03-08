package io.github.d4vinci.scrapling.spiders

import java.util.PriorityQueue

class Scheduler(
    private val includeKwargs: Boolean = false,
    private val includeHeaders: Boolean = false,
    private val keepFragments: Boolean = false,
) {
    private data class QueueEntry(
        val priority: Int,
        val counter: Long,
        val request: Request,
    )

    private val queue = PriorityQueue<QueueEntry>(compareBy<QueueEntry> { it.priority }.thenBy { it.counter })
    private val seen = linkedSetOf<String>()
    private val pending = linkedMapOf<Long, QueueEntry>()
    private val lock = Any()
    private var counter: Long = 0

    val isEmpty: Boolean
        get() = synchronized(lock) { queue.isEmpty() }

    suspend fun enqueue(request: Request): Boolean = synchronized(lock) {
        val fingerprint = request.updateFingerprint(includeKwargs, includeHeaders, keepFragments).toHexString()
        if (!request.dontFilter && fingerprint in seen) {
            return@synchronized false
        }
        seen += fingerprint
        val entry = QueueEntry(priority = -request.priority, counter = counter++, request = request)
        pending[entry.counter] = entry
        queue += entry
        true
    }

    suspend fun dequeue(): Request = synchronized(lock) {
        val entry = queue.remove()
        pending.remove(entry.counter)
        entry.request
    }

    fun size(): Int = synchronized(lock) { queue.size }

    fun snapshot(): Pair<List<Request>, Set<String>> = synchronized(lock) {
        val requests = pending.values.sortedWith(compareBy<QueueEntry> { it.priority }.thenBy { it.counter }).map { it.request }
        requests to seen.toSet()
    }

    fun restore(data: CheckpointData) = synchronized(lock) {
        seen.clear()
        seen.addAll(data.seen)
        queue.clear()
        pending.clear()
        counter = 0
        data.requests.forEach { request ->
            val entry = QueueEntry(priority = -request.priority, counter = counter++, request = request)
            pending[entry.counter] = entry
            queue += entry
        }
    }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
