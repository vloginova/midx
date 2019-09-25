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
import kotlinx.coroutines.channels.Channel
import java.io.File

private val numberOfCores = Runtime.getRuntime().availableProcessors()
@UseExperimental(ObsoleteCoroutinesApi::class)
private val defultCoroutineDispatcher = newFixedThreadPoolContext(numberOfCores + 1, "Trigram Index Builder")

private data class FileIndex(val file: String, val trigrams: TrigramSet)

fun buildIndexAsync(file: File, coroutineDispatcher: CoroutineDispatcher = defultCoroutineDispatcher): Deferred<Index> {
    val fileIndexChannel = Channel<FileIndex>(100)

    fun CoroutineScope.produceFileIndexes() {
        val fileIndexesProducer = launch {
            val isCanceled = { !isActive }
            file.forEachFileSuspend (isCanceled) {
                launch {
                    val fileIndex = FileIndex(it.path, createTrigramSet(it, isCanceled))
                    fileIndexChannel.send(fileIndex)
                }
            }
        }
        launch {
            fileIndexesProducer.join()
            fileIndexChannel.close()
        }
    }

    suspend fun reverseFileIndexes(): TrigramIndexStorage {
        val reversedTrigramIndex = TrigramIndexStorage()
        for ((filePath, trigrams) in fileIndexChannel) {
            for (trigram in trigrams) {
                val files = reversedTrigramIndex.computeIfAbsent(trigram) { ArrayList() }
                files.add(filePath)
            }
        }
        return reversedTrigramIndex
    }

    return GlobalScope.async(coroutineDispatcher) {
        produceFileIndexes()
        val storage = reverseFileIndexes()
        return@async TrigramIndex(file, storage)
    }
}

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
