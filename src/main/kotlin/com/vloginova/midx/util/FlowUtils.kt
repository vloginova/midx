package com.vloginova.midx.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
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