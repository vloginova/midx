package com.vloginova.midx

import com.vloginova.midx.impl.TrigramIndex
import com.vloginova.midx.impl.buildIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private const val USAGE = "Usage: midx-<version>.jar [--search-ignore-case] <files separated by space>"
private const val SEARCH_INVITATION = "search > "
private const val CANCEL_INVITATION = "Please wait for completion or type 'q' to cancel:"

private const val SEARCH_RESULT_LIMIT = 1000
private const val FILE_PATH_MAX_LENGTH = 60

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
        val indexDeffered = async(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            buildIndex(files).also {
                val endTime = System.currentTimeMillis()
                println("The index was built in ${endTime - startTime} ms.")
                print(SEARCH_INVITATION)
            }
        }

        while (true) {
            if (indexDeffered.isCompleted) print(SEARCH_INVITATION)

            val line = readLine()
            if (line.isNullOrEmpty()) continue

            if (!indexDeffered.isCompleted) {
                if (line == "q") {
                    cancelBuild(indexDeffered)
                    if (indexDeffered.isCancelled) return@runBlocking
                }
                println(CANCEL_INVITATION)
                continue
            }

            val index: TrigramIndex = indexDeffered.getCompleted()
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

private suspend fun cancelBuild(deferredIndex: Deferred<TrigramIndex>) {
    val timeMillis = measureTimeMillis {
        deferredIndex.cancelAndJoin()
    }
    if (deferredIndex.isCancelled) println("The build was cancelled in $timeMillis ms.")
}

private suspend fun searchInIndex(index: TrigramIndex, ignoreCase: Boolean, text: String) {
    var count = 0
    val timeMillis = measureTimeMillis {
        index.search(text, ignoreCase).takeWhile {
            if (++count % SEARCH_RESULT_LIMIT == 0) {
                print("Too many search results. Please type 'q' or anything else to continue: ")
                readLine() != "q"
            } else true
        }.collect { (file, matchingText, lineNumber, startIdx, endIdx) ->
            if (file.path.length > FILE_PATH_MAX_LENGTH) prettyPrint("...", FontStyle.BOLD)
            prettyPrint(file.path.takeLast(FILE_PATH_MAX_LENGTH), FontStyle.BOLD)
            prettyPrint(" : $lineNumber:\t", FontStyle.BOLD)

            print(matchingText.take(startIdx))
            prettyPrint(
                matchingText.substring(startIdx, endIdx),
                FontStyle.BOLD,
                FontStyle.RED
            )
            println(matchingText.drop(endIdx))
        }
    }
    // Do not show in case of suspension
    if (count < SEARCH_RESULT_LIMIT) println("The search was completed in $timeMillis ms.")
}