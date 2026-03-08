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
    private var counter: Long = 0

    val isEmpty: Boolean
        get() = queue.isEmpty()

    suspend fun enqueue(request: Request): Boolean {
        val fingerprint = request.updateFingerprint(includeKwargs, includeHeaders, keepFragments).toHexString()
        if (!request.dontFilter && fingerprint in seen) {
            return false
        }
        seen += fingerprint
        val entry = QueueEntry(priority = -request.priority, counter = counter++, request = request)
        pending[entry.counter] = entry
        queue += entry
        return true
    }

    suspend fun dequeue(): Request {
        val entry = queue.remove()
        pending.remove(entry.counter)
        return entry.request
    }

    fun size(): Int = queue.size

    fun snapshot(): Pair<List<Request>, Set<String>> {
        val requests = pending.values.sortedWith(compareBy<QueueEntry> { it.priority }.thenBy { it.counter }).map { it.request }
        return requests to seen.toSet()
    }

    fun restore(data: CheckpointData) {
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
