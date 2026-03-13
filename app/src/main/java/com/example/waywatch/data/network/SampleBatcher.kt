package com.example.waywatch.data.network

import com.example.waywatch.data.network.model.MongoApi
import com.example.waywatch.data.network.model.SubmitSampleRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayList

/**
 * Simple in-memory batcher that collects samples and periodically flushes them to the server.
 * - Batches every batchIntervalMs or when batchSize reached
 * - On 413 (payload too large), splits batch in half and retries
 * - On 429: waits and retries with backoff
 */
class SampleBatcher(
    private val api: MongoApi,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val batchIntervalMs: Long = 30_000L, // flush every 30s
    private val maxBatchSize: Int = 50 // send at most 50 samples per request
) {
    private val mutex = Mutex()
    private val queue = ArrayList<SubmitSampleRequest>()
    private var flushJob: Job? = null

    init {
        startPeriodicFlush()
    }

    fun submit(sample: SubmitSampleRequest) {
        scope.launch {
            mutex.withLock {
                queue.add(sample)
                if (queue.size >= maxBatchSize) {
                    val batch = drainQueue()
                    flushBatch(batch)
                }
            }
        }
    }

    private fun drainQueue(): List<SubmitSampleRequest> {
        val copy = ArrayList(queue)
        queue.clear()
        return copy
    }

    private fun startPeriodicFlush() {
        if (flushJob != null) return
        flushJob = scope.launch {
            while (isActive) {
                delay(batchIntervalMs)
                val batch: List<SubmitSampleRequest> = mutex.withLock { drainQueue() }
                if (batch.isNotEmpty()) {
                    flushBatch(batch)
                }
            }
        }
    }

    private fun flushBatch(batch: List<SubmitSampleRequest>) {
        scope.launch {
            try {
                // If batch is large, subdivide into chunks no larger than maxBatchSize (safe guard)
                val chunks = chunkBatch(batch, maxBatchSize)
                for (chunk in chunks) {
                    sendWithRetry(chunk)
                }
            } catch (_: Exception) {
                // On unexpected failure, re-enqueue the batch with jittered delay
                delay(5000)
                mutex.withLock {
                    queue.addAll(0, batch)
                }
            }
        }
    }

    private fun chunkBatch(batch: List<SubmitSampleRequest>, chunkSize: Int): List<List<SubmitSampleRequest>> {
        if (batch.size <= chunkSize) return listOf(batch)
        val out = ArrayList<List<SubmitSampleRequest>>()
        var i = 0
        while (i < batch.size) {
            val end = minOf(i + chunkSize, batch.size)
            out.add(batch.subList(i, end))
            i = end
        }
        return out
    }

    private suspend fun sendWithRetry(batch: List<SubmitSampleRequest>) {
        var attempts = 0
        var delayMs = 1000L

        while (attempts < 6) {
            attempts++
            try {
                val resp = api.submitSamples(batch)
                if (resp.ok) return
                val err = resp.error ?: resp.message ?: "unknown"
                if (err.contains("Too many samples", ignoreCase = true)) {
                    if (batch.size <= 1) return
                    val mid = batch.size / 2
                    val first = batch.subList(0, mid)
                    val second = batch.subList(mid, batch.size)
                    sendWithRetry(first)
                    sendWithRetry(second)
                    return
                }
                if (err.contains("rate", ignoreCase = true) || err.contains("limit", ignoreCase = true)) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(60_000L)
                    continue
                }
                throw Exception("Server error: $err")
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                if (code == 413) {
                    if (batch.size <= 1) return
                    val mid = batch.size / 2
                    val first = batch.subList(0, mid)
                    val second = batch.subList(mid, batch.size)
                    sendWithRetry(first)
                    sendWithRetry(second)
                    return
                }
                if (code == 429) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(60_000L)
                    continue
                }
                throw e
            } catch (_: Exception) {
                // Network or serialization errors: backoff and retry
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(60_000L)
            }
        }
        // after attempts exhausted, re-enqueue to front
        mutex.withLock {
            queue.addAll(0, batch)
        }
    }
}
