package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.util.collections.TrigramIndexStorage
import com.vloginova.midx.util.collections.TrigramSet
import com.vloginova.midx.util.createTrigramSet
import com.vloginova.midx.util.fullTextSearch
import com.vloginova.midx.util.parallelMapNotNull
import com.vloginova.midx.util.walkTextFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

private val availableProcessors = Runtime.getRuntime().availableProcessors()
@UseExperimental(ObsoleteCoroutinesApi::class)
private val defaultDispatcher = lazy { newFixedThreadPoolContext(availableProcessors + 1, "Trigram Index Builder") }

// Number of files that can be processed at the same simultaneously
private const val trigramBuilderParallelism = 500

/**
 * Implementation of [Index] represented as a reverse index with a trigram as a key. The class is immutable, so it can
 * be safely used for searching in multi thread context.
 *
 * Index does not support incremental updates.
 * TODO: accept several files
 */
class TrigramIndex(private val rootDirectory: File, private val indexStorage: TrigramIndexStorage) : Index {

    /**
     * Searches for [text] in the [TrigramIndex]. The implementation is effective for searches of input of length
     * greater that 3. For short inputs it searches fulltext.
     *
     * [processMatch] is called as soon as it is found.
     */
    override fun search(text: String, processMatch: (SearchResult) -> Unit) {
        if (text.isEmpty()) return

        if (text.length < 3) {
            rootDirectory.walkTextFiles().forEach {
                it.fullTextSearch(text, processMatch)
            }
        } else {
            matchingFileCandidates(text, indexStorage).forEach { filePath ->
                filePath.fullTextSearch(text, processMatch)
            }
        }
    }

    private fun matchingFileCandidates(text: String, indexIndexStorage: TrigramIndexStorage): Collection<File> {
        check(text.length >= 3) { "Cannot search in index inputs with length les than 3" }

        val trigrams = createTrigramSet(text)
        var intersection = indexIndexStorage[trigrams.first()]?.toSet() ?: emptySet()
        for (trigram in trigrams) {
            indexIndexStorage[trigram]?.let { intersection = intersection.intersect(it) }
        }
        return intersection
    }

}

private data class FileIndex(val file: File, val trigrams: TrigramSet)

/**
 * Initiate [Index] building with [context] for [rootDirectory]. If a context doesn't have any
 * [ContinuationInterceptor.Key], [defaultDispatcher] will be used.
 *
 * TODO: Handle unprocessed files strategy
 */
fun buildIndexAsync(
    rootDirectory: File,
    context: CoroutineContext = EmptyCoroutineContext,
    handleUnprocessedFile: ((String) -> Unit) = { _ -> }
): Deferred<TrigramIndex> {
    var contextWithDispatcher = context
    if (context[ContinuationInterceptor.Key] == null) contextWithDispatcher += defaultDispatcher.value
    return GlobalScope.async(contextWithDispatcher) {
        buildIndex(rootDirectory, handleUnprocessedFile)
    }
}

suspend fun buildIndex(
    rootDirectory: File,
    handleUnprocessedFile: ((String) -> Unit) = { _ -> }
): TrigramIndex {
    val storage = TrigramIndexStorage()
    // The index can be efficiently built with MapReduce pattern. Map runs in parallel, which easily improves
    // performance because of huge amount of IO operations together with computational ones. Reduce, however,
    // only uses CPU resources, and it is executed in parallel with map step. Experiments didn't show any
    // significant performance improvement when running reduce step in parallel, so it was decided not to complicate
    // the solution.
    rootDirectory.walkTextFiles().asFlow()
        .parallelMapNotNull(trigramBuilderParallelism) { file ->
            tryCreateFileIndex(file, handleUnprocessedFile, coroutineContext::ensureActive)
        }
        .collect { fileIndex ->
            storage.populateWith(fileIndex)
        }
    return TrigramIndex(rootDirectory, storage)
}


private fun tryCreateFileIndex(
    file: File,
    handleUnprocessedFile: (String) -> Unit,
    checkCancelled: () -> Unit
): FileIndex? {
    return try {
        val trigrams = createTrigramSet(file, checkCancelled = checkCancelled)
        FileIndex(file, trigrams)
    } catch (_: IOException) {
        handleUnprocessedFile(file.path)
        null
    }
}

private fun TrigramIndexStorage.populateWith(fileIndex: FileIndex) {
    for (trigram in fileIndex.trigrams) {
        computeIfAbsent(trigram) { ArrayList() }
            .also { files -> files.add(fileIndex.file) }
    }
}
