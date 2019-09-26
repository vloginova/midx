package com.vloginova.midx

import com.vloginova.midx.api.Index
import com.vloginova.midx.impl.buildIndexAsync
import kotlinx.coroutines.*
import java.io.File
import kotlin.system.measureTimeMillis

const val inviteToSearch = "Please type something request to search:"
const val inviteToWaitOrCancel = "Please wait for its completion or type 'cancel':"
const val inviteToToBuild = "Please input a root directory path to start an index build:"

@UseExperimental(ExperimentalCoroutinesApi::class)
fun main() {
    println("Welcome to Midx demo.")
    println(inviteToToBuild)
    val root = getInputDirectory()

    val startTime = System.currentTimeMillis()
    val deferredIndex = buildIndexAsync(root)

    awaitInBackground(deferredIndex, startTime)
        .invokeOnCompletion {
            if (!deferredIndex.isCancelled) println(inviteToSearch)
        }

    println("Index is building. $inviteToWaitOrCancel")
    while (true) {
        val line = readLine()
        if (line.isNullOrEmpty()) continue

        if (!deferredIndex.isCompleted) {
            if (line == "cancel") {
                cancelBuild(deferredIndex)
                if (deferredIndex.isCancelled) return
            }
            println("Build is not yet completed. $inviteToWaitOrCancel")
            continue
        }

        val index: Index = runBlocking { deferredIndex.await() }
        searchInIndex(index, line)
        println(inviteToSearch)
    }
}

private fun getInputDirectory(): File {
    while (true) {
        val filePath = readLine()
        if (filePath.isNullOrEmpty()) continue

        val file = File(filePath)
        when {
            !file.exists() -> println("'$filePath' doesn't exist. Please try again:")
            !file.canRead() -> println("'$filePath' cannot be read. Please try again:")
            else -> return file
        }
    }
}

private fun awaitInBackground(deferredIndex: Deferred<Index>, startTime: Long): Job =
    GlobalScope.launch {
        deferredIndex.await()
        val endTime = System.currentTimeMillis()
        println("The index was built in ${endTime - startTime} ms.")
    }

private fun cancelBuild(deferredIndex: Deferred<Index>) {
    val timeMillis = measureTimeMillis {
        runBlocking {
            deferredIndex.cancelAndJoin()
        }
    }
    if (deferredIndex.isCancelled) println("The build was cancelled in $timeMillis ms")
}

private fun searchInIndex(index: Index, text: String) {
    val timeMillis = measureTimeMillis {
        index.search(text) { (fileName, matchingText, startIdx, endIdx) ->
            prettyPrint(fileName, FontStyle.BOLD)
            prettyPrint(": ", FontStyle.BOLD)

            print(matchingText.take(startIdx))
            prettyPrint(
                matchingText.substring(startIdx, endIdx),
                FontStyle.BOLD,
                FontStyle.RED
            )
            println(matchingText.drop(endIdx))
        }
    }
    println("The search was completed in $timeMillis ms.")
}