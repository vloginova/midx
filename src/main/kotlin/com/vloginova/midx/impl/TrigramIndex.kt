package com.vloginova.midx.impl

import com.vloginova.midx.api.IGNORE_DO_NOTHING
import com.vloginova.midx.api.IOExceptionHandler
import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.util.*
import com.vloginova.midx.util.collections.IntKeyMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

private val availableProcessors = Runtime.getRuntime().availableProcessors()
@UseExperimental(ObsoleteCoroutinesApi::class)
private val defaultDispatcher by lazy { newFixedThreadPoolContext(availableProcessors + 1, "Trigram Index Builder") }

// Number of files that can be processed at the same simultaneously
private const val trigramIndexParallelism = 500

/**
 * [TrigramIndex] is represented as a reverse index with a trigram as a key. The class is immutable, so it can
 * be safely used for searching in multi thread context.
 *
 * Index does not support incremental updates.
 */
class TrigramIndex internal constructor(
    private val rootDirectory: Iterable<File>,
    private val indexStorage: TrigramIndexStorage
) : Index {

    /**
     * Searches for [text] in the [TrigramIndex]. The implementation is effective for searches of input of length
     * greater that 3. For short inputs it searches fulltext.
     */
    override suspend fun search(
        text: String,
        ignoreCase: Boolean,
        ioExceptionHandler: IOExceptionHandler,
        processMatch: (SearchResult) -> Unit
    ) {
        if (text.isEmpty()) return

        val fileSequence =
            if (text.length < 3) rootDirectory.walkFiles(ioExceptionHandler)
            else matchingFileCandidates(text, indexStorage)

        fileSequence.asFlow()
            .parallelFilter(trigramIndexParallelism) { file ->
                file.tryProcess(ioExceptionHandler) {
                    file.hasTextContent()
                } ?: false
            }
            .parallelMapNotNull(trigramIndexParallelism) { file ->
                file.tryProcess(ioExceptionHandler) {
                    file.fullTextSearch(text, ignoreCase)
                }
            }.collect {
                it.forEach(processMatch)
            }
    }

    fun searchAsync(
        text: String,
        ignoreCase: Boolean = false,
        ioExceptionHandler: IOExceptionHandler = IGNORE_DO_NOTHING,
        context: CoroutineContext = EmptyCoroutineContext,
        processMatch: (SearchResult) -> Unit
    ): Deferred<Unit> {
        return runWithDefaultDispatcherAsync(context) {
            search(text, ignoreCase, ioExceptionHandler, processMatch)
        }
    }

    private fun matchingFileCandidates(text: String, indexIndexStorage: TrigramIndexStorage): Sequence<File> {
        check(text.length >= 3) { "Cannot search in index inputs with length les than 3" }

        val trigrams = TrigramSet.from(text)
        var intersection = indexIndexStorage[trigrams.first()]?.toSet() ?: emptySet()
        for (trigram in trigrams) {
            indexIndexStorage[trigram]?.let { intersection = intersection.intersect(it) }
        }
        return intersection.asSequence()
    }

}

internal data class FileIndex(val file: File, val trigrams: TrigramSet)

/**
 * [TrigramIndexStorage] is an internal structure of [TrigramIndex]. Maps trigrams to the collection of files where
 * it is present.
 */
internal class TrigramIndexStorage : Iterable<IntKeyMap.Entry<MutableList<File>>> {
    private val internalMap = IntKeyMap<MutableList<File>>()

    val size: Int
        get() = internalMap.size

    operator fun get(key: Int): MutableList<File>? = internalMap[key]

    override fun iterator(): Iterator<IntKeyMap.Entry<MutableList<File>>> = internalMap.iterator()

    /**
     * Reverses [FileIndex] and populates [TrigramIndexStorage]
     */
    fun populateWith(fileIndex: FileIndex) {
        for (trigram in fileIndex.trigrams) {
            internalMap.computeIfAbsent(trigram) { ArrayList() }
                .also { files -> files.add(fileIndex.file) }
        }
    }
}

/**
 * Initiate [TrigramIndex] building with [context] for [files]. If a context doesn't have any
 * [ContinuationInterceptor.Key], [defaultDispatcher] will be used.
 */
fun buildIndexAsync(
    files: List<File>,
    ioExceptionHandler: IOExceptionHandler = IGNORE_DO_NOTHING,
    context: CoroutineContext = EmptyCoroutineContext
): Deferred<TrigramIndex> {
    return runWithDefaultDispatcherAsync(context) {
        buildIndex(files, ioExceptionHandler)
    }
}

suspend fun buildIndex(
    files: Iterable<File>,
    ioExceptionHandler: IOExceptionHandler = IGNORE_DO_NOTHING
): TrigramIndex {
    val storage = TrigramIndexStorage()
    // The index can be efficiently built with MapReduce pattern. Map runs in parallel, which easily improves
    // performance because of huge amount of IO operations together with computational ones. Reduce, however,
    // only uses CPU resources, and it is executed in parallel with map step. Experiments didn't show any
    // significant performance improvement when running reduce step in parallel, so it was decided not to complicate
    // the solution.
    val filesCopy = files.toList()
    filesCopy.walkFiles(ioExceptionHandler).asFlow()
        .parallelFilter(trigramIndexParallelism) { file ->
            file.tryProcess(ioExceptionHandler) {
                file.hasTextContent()
            } ?: false
        }
        .parallelMapNotNull(trigramIndexParallelism) { file ->
            tryCreateFileIndex(file, ioExceptionHandler, coroutineContext::ensureActive)
        }
        .collect { fileIndex ->
            storage.populateWith(fileIndex)
        }
    return TrigramIndex(filesCopy, storage)
}

private fun tryCreateFileIndex(
    file: File,
    ioExceptionHandler: IOExceptionHandler,
    checkCancelled: () -> Unit
): FileIndex? {
    return file.tryProcess(ioExceptionHandler) {
        val trigrams = TrigramSet.from(file, checkCancelled = checkCancelled)
        FileIndex(file, trigrams)
    }
}

private fun <T> runWithDefaultDispatcherAsync(context: CoroutineContext, block: suspend () -> T): Deferred<T> {
    var contextWithDispatcher = context
    if (context[ContinuationInterceptor.Key] == null) contextWithDispatcher += defaultDispatcher
    return GlobalScope.async(contextWithDispatcher) {
        block()
    }
}
