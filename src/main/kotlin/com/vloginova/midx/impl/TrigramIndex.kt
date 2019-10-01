package com.vloginova.midx.impl

import com.vloginova.midx.api.IOExceptionHandler
import com.vloginova.midx.api.IOExceptionHandlers.IGNORE
import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.util.*
import com.vloginova.midx.collections.IntKeyMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

private val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()

// For file processing operations
private const val TRIGRAM_INDEX_CONCURRENCY_LEVEL = 128

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
    override fun search(
        text: String,
        ignoreCase: Boolean,
        ioExceptionHandler: IOExceptionHandler,
        context: CoroutineContext
    ): Flow<SearchResult> {
        if (text.isEmpty()) return emptyFlow()

        val fileSequence =
            if (text.length < 3) rootDirectory.walkFiles(ioExceptionHandler)
            else matchingFileCandidates(text)

        return fileSequence.asFlow()
            .concurrentFilter(Dispatchers.IO + context, TRIGRAM_INDEX_CONCURRENCY_LEVEL) { file ->
                file.tryProcess(ioExceptionHandler) {
                    file.hasTextContent()
                } ?: false
            }
            .concurrentMapNotNull(Dispatchers.IO + context, TRIGRAM_INDEX_CONCURRENCY_LEVEL) { file ->
                file.tryProcess(ioExceptionHandler) {
                    file.fullTextSearch(text, ignoreCase)
                }
            }
            .buffer(Channel.UNLIMITED)
            .flatMapConcat { results ->
                results.asFlow()
            }
    }

    private fun matchingFileCandidates(text: String): Sequence<File> {
        check(text.length >= 3) { "Cannot search in index inputs with length les than 3" }
        val trigrams = TrigramSet.from(text)
        return indexStorage.getIntersectionOf(trigrams).asSequence()
    }

}

internal data class FileIndex(val file: File, val trigrams: TrigramSet)

/**
 * [TrigramIndexStorage] is an internal structure of [TrigramIndex]. Maps trigrams to the collection of files where
 * it is present.
 */
internal class TrigramIndexStorage(private val partitions: Collection<TrigramIndexStoragePartition>) {

    fun getIntersectionOf(trigrams: TrigramSet): Collection<File> {
        return partitions
            .map { partition -> partition.getIntersectionOf(trigrams) }
            .fold(mutableSetOf()) { acc, set -> acc.apply { addAll(set) } }
    }

}

internal typealias TrigramIndexEntry = IntKeyMap.Entry<MutableList<File>>

/**
 * [TrigramIndexStoragePartition] is a partition of [TrigramIndex]. Each partition contains information on different
 * set of files, so that each partition is a complete index for files it represents.
 */
internal class TrigramIndexStoragePartition : Iterable<TrigramIndexEntry> {
    private val internalMap = IntKeyMap<MutableList<File>>()

    val size: Int
        get() = internalMap.size

    operator fun get(key: Int): MutableList<File>? = internalMap[key]

    override fun iterator(): Iterator<TrigramIndexEntry> = internalMap.iterator()

    /**
     * Reverses [FileIndex] and populates [TrigramIndexStorage]
     */
    fun populateWith(fileIndex: FileIndex) {
        for (trigram in fileIndex.trigrams) {
            internalMap.computeIfAbsent(trigram) { ArrayList() }.add(fileIndex.file)
        }
    }

    fun getIntersectionOf(trigrams: TrigramSet): Collection<File> {
        var intersection = this[trigrams.first()]?.toSet() ?: emptySet()
        for (trigram in trigrams) {
            if (intersection.isEmpty()) return intersection
            intersection = intersection.intersect(this[trigram] ?: emptyList())
        }
        return intersection
    }

}

/**
 * Initiate [TrigramIndex] building with [context] for [files]. If a context doesn't have any
 * [ContinuationInterceptor.Key], default dispatchers will be used. [files] should not contain duplicates.
 */
suspend fun buildIndex(
    files: Iterable<File>,
    ioExceptionHandler: IOExceptionHandler = IGNORE,
    context: CoroutineContext = EmptyCoroutineContext
): TrigramIndex {
    val storages = files.walkFiles(ioExceptionHandler).asFlow()
        .concurrentFilter(Dispatchers.IO + context, TRIGRAM_INDEX_CONCURRENCY_LEVEL) { file ->
            file.tryProcess(ioExceptionHandler) {
                file.hasTextContent()
            } ?: false
        }
        .concurrentMapNotNull(Dispatchers.IO + context, TRIGRAM_INDEX_CONCURRENCY_LEVEL) { file ->
            tryCreateFileIndex(file, ioExceptionHandler, coroutineContext::ensureActive)
        }
        .buffer(Channel.UNLIMITED)
        .partitionFold(
            Dispatchers.Default + context,
            AVAILABLE_PROCESSORS,
            ::TrigramIndexStoragePartition
        ) { storage, fileIndex ->
            storage.apply { populateWith(fileIndex) }
        }
    return TrigramIndex(files, TrigramIndexStorage(storages))
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
