package com.vloginova.midx

import com.vloginova.midx.impl.TrigramIndex
import com.vloginova.midx.impl.buildIndexAsync
import kotlinx.coroutines.*
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private const val USAGE = "Usage: midx-<version>.jar [--search-ignore-case] <files separated by space>"
private const val SEARCH_INVITATION = "search > "
private const val CANCEL_INVITATION = "Please wait for completion or type 'cancel':"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(USAGE)
    }
    val searchIgnoreCase = args[0] == "--search-ignore-case"
    val files = args.drop(if (searchIgnoreCase) 1 else 0)
        .map { File(it) }

    if (files.isEmpty()) {
        println(USAGE)
        exitProcess(-1)
    }

    checkFiles(files)

    println("Indexing...")
    println(CANCEL_INVITATION)

    runBlocking {
        val startTime = System.currentTimeMillis()
        val deferredIndex = buildIndexAsync(files)

        awaitInBackground(deferredIndex, startTime)
            .invokeOnCompletion {
                if (!deferredIndex.isCancelled) print(SEARCH_INVITATION)
            }

        while (true) {
            if (deferredIndex.isCompleted) print(SEARCH_INVITATION)

            val line = readLine()
            if (line.isNullOrEmpty()) continue

            if (!deferredIndex.isCompleted) {
                if (line == "cancel") {
                    cancelBuild(deferredIndex)
                    if (deferredIndex.isCancelled) return@runBlocking
                }
                println(CANCEL_INVITATION)
                continue
            }

            val index: TrigramIndex = deferredIndex.getCompleted()
            searchInIndex(index, searchIgnoreCase, line)
        }
    }
}

private fun checkFiles(files: List<File>) {
    var incorrectFileFound = false
    files.forEach { file ->
        if (!file.canRead()) {
            System.err.println("'${file.path}' cannot be read.")
            incorrectFileFound = true
        }
    }
    if (incorrectFileFound) exitProcess(-1)
}

private fun awaitInBackground(deferredIndex: Deferred<TrigramIndex>, startTime: Long): Job =
    GlobalScope.launch {
        deferredIndex.await()
        val endTime = System.currentTimeMillis()
        println("The index was built in ${endTime - startTime} ms.")
    }

private suspend fun cancelBuild(deferredIndex: Deferred<TrigramIndex>) {
    val timeMillis = measureTimeMillis {
        deferredIndex.cancelAndJoin()
    }
    if (deferredIndex.isCancelled) println("The build was cancelled in $timeMillis ms")
}

private suspend fun searchInIndex(index: TrigramIndex, ignoreCase: Boolean, text: String) {
    val timeMillis = measureTimeMillis {
        index.searchAsync(text, ignoreCase) { (file, matchingText, lineNumber, startIdx, endIdx) ->
            prettyPrint(file.path.takeLast(60), FontStyle.BOLD)
            prettyPrint(" : $lineNumber:\t", FontStyle.BOLD)

            print(matchingText.take(startIdx))
            prettyPrint(
                matchingText.substring(startIdx, endIdx),
                FontStyle.BOLD,
                FontStyle.RED
            )
            println(matchingText.drop(endIdx))
        }.await()
    }
    println("The search was completed in $timeMillis ms.")
}