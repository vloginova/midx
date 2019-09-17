package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.util.forEachFile
import com.vloginova.midx.util.forEachFileSuspend
import com.vloginova.midx.util.toTrigramSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The thread safe implementation of index, keys are implemented as trigrams
 * TODO: accept several files
 */
class TrigramIndex(private val file: File) : Index {
    private val trigramsProducersCount = 10
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val lock = ReentrantReadWriteLock()

    /* Below two fields are a part of shared mutable state of TrigramIndex. They are guarded by lock.
    * TODO: describe when lock is not needed */
    @Volatile
    private var storage: Map<Int, List<String>> = emptyMap()
    @Volatile
    private var indexBuildingJob: Job? = null

    override fun build() {
        lock.write {
            check(!isBuildInProgress()) { "Build is already in progress" }
            indexBuildingJob = coroutineScope.launch {
                val filesChannel = Channel<File>()
                val trigramsChannel = Channel<Pair<String, Set<Int>>>()

                launch { produceFiles(file, filesChannel) }
                val trigramProducerJobs = ArrayList<Job>(trigramsProducersCount)
                repeat(trigramsProducersCount) {
                    val job = launch { produceTrigrams(filesChannel, trigramsChannel) }
                    trigramProducerJobs.add(job)
                }
                launch { fillStorageWithTrigrams(trigramsChannel) }
                launch { closeTrigramsChannelWhenReady(trigramProducerJobs, trigramsChannel) }
            }
        }
    }

    override fun search(text: String, processMatch: (String, String, Int) -> Unit) {
        if (text.isEmpty()) return

        var indexStorage = emptyMap<Int, List<String>>()
        lock.read {
            check(isBuildCompleted()) { "Cannot search when index is not yet built" }
            indexStorage = storage
        }

        if (text.length < 3) {
            file.forEachFile { file ->
                file.fullTextSearch(text, processMatch)
            }
            return
        }

        matchingFileCandidates(text, indexStorage).forEach { filePath ->
            File(filePath).fullTextSearch(text, processMatch)
        }
    }

    override fun cancelBuild() {
        lock.write {
            runBlocking { indexBuildingJob?.cancelAndJoin() }
            indexBuildingJob = null
            storage = emptyMap()
        }
    }

    override fun waitForBuildCompletion(): Boolean {
        runBlocking { indexBuildingJob?.join() }
        return isBuildCompleted()
    }

    private fun isBuildCompleted(): Boolean {
        return indexBuildingJob?.isCompleted ?: false
    }

    private fun isBuildInProgress(): Boolean {
        return indexBuildingJob?.isActive ?: false
    }

    private suspend fun produceFiles(file: File, filesChannel: Channel<File>) {
        file.forEachFileSuspend { filesChannel.send(it) }
        filesChannel.close()
    }

    private suspend fun produceTrigrams(filesChannel: Channel<File>, resultChannel: Channel<Pair<String, Set<Int>>>) {
        for (file in filesChannel) {
            resultChannel.send(Pair(file.path, file.readText(Charsets.UTF_8).toTrigramSet()))
        }
    }

    private suspend fun fillStorageWithTrigrams(resultChannel: Channel<Pair<String, Set<Int>>>) {
        val indexStorage = HashMap<Int, MutableList<String>>()
        for ((fileName, trigrams) in resultChannel) {
            trigrams.forEach { word ->
                indexStorage.putIfAbsent(word, ArrayList())
                indexStorage[word]!!.add(fileName)
            }
        }
        storage = indexStorage
    }

    private suspend fun closeTrigramsChannelWhenReady(
        jobs: Collection<Job>,
        trigramsChannel: Channel<Pair<String, Set<Int>>>
    ) {
        jobs.forEach { it.join() }
        trigramsChannel.close()
    }

    private fun File.fullTextSearch(
        text: String,
        processMatch: (String, String, Int) -> Unit
    ) {
        forEachLine(Charsets.UTF_8) { line ->
            var textPosition = line.indexOf(text)
            while (textPosition != -1) {
                // TODO: exception handling
                processMatch(path, line, textPosition)
                textPosition = line.indexOf(text, textPosition + 1)
            }
        }
    }

    private fun matchingFileCandidates(text: String, indexStorage: Map<Int, List<String>>): Collection<String> {
        val tokens = text.toTrigramSet()
        if (tokens.isEmpty()) return emptyList()

        var intersection = indexStorage[tokens.first()]?.toSet() ?: emptySet()
        tokens.forEach { token ->
            indexStorage[token]?.let { intersection = intersection.intersect(it) }
        }
        return intersection
    }

}
