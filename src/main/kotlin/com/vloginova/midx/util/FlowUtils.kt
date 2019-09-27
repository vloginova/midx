package com.vloginova.midx.util

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Maps receiver flow to another in parallel with level [parallelism].
 * Buffer size of result flow must not be changed, it won't have any effect on parallelism level.
 */
internal inline fun <T, R> Flow<T>.parallelMapNotNull(
    parallelism: Int,
    crossinline transform: suspend (value: T) -> R?
): Flow<R> =
    channelFlow {
        val semaphore = Semaphore(parallelism)
        collect { value ->
            semaphore.acquire()
            launch {
                val result = transform(value) ?: return@launch
                send(result)
            }.invokeOnCompletion {
                semaphore.release()
            }
        }
    }.buffer(parallelism)

/**
 * Filters values from the receiver flow with [predicate] in parallel with level [parallelism].
 * Buffer size of result flow must not be changed, it won't have any effect on parallelism level.
 */
internal inline fun <T> Flow<T>.parallelFilter(
    parallelism: Int,
    crossinline predicate: suspend (T) -> Boolean
): Flow<T> =
    channelFlow {
        val semaphore = Semaphore(parallelism)
        collect { value ->
            semaphore.acquire()
            launch {
                if (predicate(value)) send(value)
            }.invokeOnCompletion {
                semaphore.release()
            }
        }
    }.buffer(parallelism)