// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.tts

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Plugin-local, one model/connection at a time. Active leases cannot be expired or replaced. */
class ResidentResource<K, T : Any>(private val scope: CoroutineScope,
    private val dispose: suspend (T) -> Unit, private val idleMillis: Long = 120_000) {
    private val mutex = Mutex()
    private var key: K? = null
    private var value: T? = null
    private var resident = false
    private var playback = false
    private var idle: Job? = null
    private var generation = 0L
    private var closed = false

    suspend fun <R> use(next: K, create: suspend () -> T, block: suspend (T) -> R): R = mutex.withLock {
        check(!closed)
        cancelIdle()
        try {
            if (value == null || key != next) {
                clear()
                value = create()
                key = next
            }
            block(requireNotNull(value))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            withContext(NonCancellable) { clear() }
            throw error
        } finally { scheduleIdle() }
    }
    suspend fun setResident(enabled: Boolean) = mutex.withLock {
        resident = enabled
        cancelIdle()
        scheduleIdle()
    }
    suspend fun setActive(active: Boolean) = mutex.withLock { playback = active; cancelIdle(); scheduleIdle() }
    suspend fun release() = mutex.withLock { playback = false; cancelIdle(); clear() }
    suspend fun close() = mutex.withLock { closed = true; cancelIdle(); clear() }
    private fun cancelIdle() { generation++; idle?.cancel(); idle = null }
    private fun scheduleIdle() {
        if (resident || playback || closed || value == null) return
        val expected = generation
        idle = scope.launch {
            delay(idleMillis)
            mutex.withLock { if (generation == expected && !resident && !playback) { idle = null; clear() } }
        }
    }
    private suspend fun clear() {
        val previous = value
        value = null; key = null
        if (previous != null) dispose(previous)
    }
}
