package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.api.SearchResult
import com.vloginova.midx.util.collections.TrigramIndexStorage
import com.vloginova.midx.util.collections.TrigramSet
import com.vloginova.midx.util.createTrigramSet
import com.vloginova.midx.util.forEachFile
import com.vloginova.midx.util.forEachFileSuspend
import com.vloginova.midx.util.fullTextSearch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.IOException

private val availableProcessors = Runtime.getRuntime().availableProcessors()
@UseExperimental(ObsoleteCoroutinesApi::class)
private val defaultCoroutineDispatcher = newFixedThreadPoolContext(availableProcessors + 1, "Trigram Index Builder")

/**
 * The thread safe implementation of index, keys are implemented as trigrams
 * TODO: accept several files
 */
class TrigramIndex(private val file: File, private val indexStorage: TrigramIndexStorage) : Index {

    override fun search(text: String, processMatch: (SearchResult) -> Unit) {
        if (text.isEmpty()) return

        if (text.length < 3) {
            file.forEachFile {
                it.fullTextSearch(text, processMatch)
            }
        } else {
            matchingFileCandidates(text, indexStorage).forEach { filePath ->
                File(filePath).fullTextSearch(text, processMatch)
            }
        }
    }

    private fun matchingFileCandidates(text: String, indexIndexStorage: TrigramIndexStorage): Collection<String> {
        check(text.length >= 3) { "Cannot search in index inputs with length les than 3" }

        val trigrams = createTrigramSet(text)
        var intersection = indexIndexStorage[trigrams.first()]?.toSet() ?: emptySet()
        for (trigram in trigrams) {
            indexIndexStorage[trigram]?.let { intersection = intersection.intersect(it) }
        }
        return intersection
    }

}

private data class FileIndex(val file: String, val trigrams: TrigramSet)

@ExperimentalCoroutinesApi
@UseExperimental(InternalCoroutinesApi::class)
fun buildIndexAsync(
    file: File,
    dispatcher: CoroutineDispatcher = defaultCoroutineDispatcher,
    handleUnprocessedFile: ((String) -> Unit) = { _ -> }
): Deferred<Index> =
    GlobalScope.async(dispatcher) {
        val storage = fileFlow(file)
            .parallelMapNotNull(1000) {
                produceFileIndexes(it, handleUnprocessedFile)
            }
            .fold(TrigramIndexStorage()) { storage: TrigramIndexStorage, fileIndex: FileIndex ->
                storage.populateWith(fileIndex)
                return@fold storage
            }
        return@async TrigramIndex(file, storage)
    }

private fun fileFlow(file: File): Flow<File> =
    flow {
        file.forEachFileSuspend { emit(it) }
    }

private fun CoroutineScope.produceFileIndexes(file: File, handleUnprocessedFile: ((String) -> Unit)): FileIndex? {
    try {
        return FileIndex(file.path, createTrigramSet(file, isCanceled = { !isActive }))
    } catch (_: IOException) {
        handleUnprocessedFile(file.path)
    }
    return null
}

private fun TrigramIndexStorage.populateWith(fileIndex: FileIndex) {
    for (trigram in fileIndex.trigrams) {
         computeIfAbsent(trigram) { ArrayList() }
             .also { files -> files.add(fileIndex.file) }
    }
}

@ExperimentalCoroutinesApi
private inline fun <T, R> Flow<T>.parallelMapNotNull(
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
