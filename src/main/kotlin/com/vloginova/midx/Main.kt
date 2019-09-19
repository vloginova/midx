package com.vloginova.midx

import com.vloginova.midx.impl.TrigramIndex
import com.vloginova.midx.util.FontStyle
import com.vloginova.midx.util.prettyPrint
import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    require(args.size == 2) { "Exactly two arguments expected, was ${args.size}" }

    val simpleIndex = TrigramIndex(File(args[0]))
    val timeMillis = measureTimeMillis {
        simpleIndex.build()
        simpleIndex.waitForBuildCompletion()
    }
    val text = args[1]
    simpleIndex.search(text) { fileName, line, startIdx ->
        prettyPrint(fileName, FontStyle.BOLD)
        prettyPrint(": ", FontStyle.BOLD)

        print(line.take(startIdx))
        prettyPrint(line.substring(startIdx, startIdx + text.length), FontStyle.BOLD, FontStyle.RED)
        println(line.drop(startIdx + text.length))
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