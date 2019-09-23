package com.vloginova.midx

import com.vloginova.midx.api.Index
import com.vloginova.midx.impl.buildIndexAsync
import com.vloginova.midx.util.FontStyle
import com.vloginova.midx.util.prettyPrint
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    require(args.size == 2) { "Exactly two arguments expected, was ${args.size}" }

    var index : Index? = null
    val timeMillis = measureTimeMillis {
        val simpleIndex = buildIndexAsync(File(args[0]))
        runBlocking {
            index = simpleIndex.await()
        }
    }

    val text = args[1]
    index?.search(text) { (fileName, matchingText, startIdx, endIdx) ->
        prettyPrint(fileName, FontStyle.BOLD)
        prettyPrint(": ", FontStyle.BOLD)

        print(matchingText.take(startIdx))
        prettyPrint(matchingText.substring(startIdx, endIdx), FontStyle.BOLD, FontStyle.RED)
        println(matchingText.drop(endIdx))
    }
    println("Indexing time: $timeMillis")

//    println("Collisions count: ${AddOnlyIntSet.collisionsCounter}")
//    println("Resize count: ${AddOnlyIntSet.counter}")
//    println("Bytes total : ${AddOnlyIntSet.totalBytes.get() / 1024 / 1024}M")

//    val file = File("stat.csv")
//    file.createNewFile()
//    pairs.shuffled().take(10000).forEach {
//        file.appendText( "${it.first},${it.second}\n")
//    }
}