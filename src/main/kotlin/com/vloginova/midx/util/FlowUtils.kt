package com.vloginova.midx.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext

/**
 * Maps receiver flow to another with a concurrency level [concurrencyLevel].
 */
internal inline fun <T, R> Flow<T>.concurrentMapNotNull(
    context: CoroutineContext,
    concurrencyLevel: Int,
    crossinline transform: suspend (value: T) -> R?
): Flow<R> {
    require(concurrencyLevel >= 1) { "Concurrency level cannot be less than 1" }
    return channelFlow {
        launch(context) {
            val semaphore = Semaphore(concurrencyLevel)
            collect { value ->
                semaphore.acquire()
                launch {
                    transform(value)?.let { send(it) }
                }.invokeOnCompletion {
                    semaphore.release()
                }
            }
        }
    }
}

/**
 * Filters values from the receiver with a concurrency level [concurrencyLevel].
 */
internal inline fun <T> Flow<T>.concurrentFilter(
    context: CoroutineContext,
    concurrencyLevel: Int,
    crossinline predicate: suspend (T) -> Boolean
): Flow<T> {
    require(concurrencyLevel >= 1) { "Concurrency level cannot be less than 1" }
    return channelFlow {
        launch(context) {
            val semaphore = Semaphore(concurrencyLevel)
            collect { value ->
                semaphore.acquire()
                launch {
                    if (predicate(value)) send(value)
                }.invokeOnCompletion {
                    semaphore.release()
                }
            }
        }
    }
}

/**
 * Folds receiver flow concurrently into [partitionNumber] of partitions.
 */
internal suspend inline fun <T, R> Flow<T>.partitionFold(
    context: CoroutineContext,
    partitionNumber: Int,
    crossinline produceInitial: () -> R,
    crossinline operation: suspend (acc: R, value: T) -> R
): Collection<R> {
    require(partitionNumber >= 1) { "Partitions number cannot be less than 1" }
    return withContext(context) {
        val channel = produceIn(this)
        (0 until partitionNumber).map {
            async {
                var accumulator = produceInitial()
                for (value in channel) {
                    accumulator = operation(accumulator, value)
                }
                accumulator
            }
        }.awaitAll()
    }
}
