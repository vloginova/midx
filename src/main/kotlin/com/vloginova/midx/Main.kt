package com.vloginova.midx

import com.vloginova.midx.impl.SimpleIndex
import com.vloginova.midx.util.FontStyle
import com.vloginova.midx.util.prettyPrint
import java.io.File

fun main(args: Array<String>) {
    if (args.size != 2) throw IllegalArgumentException("Exactly two arguments expected, was ${args.size}")

    val simpleIndex = SimpleIndex()
    simpleIndex.build(File(args[0]))
    val text = args[1]
    simpleIndex.search(text) { fileName, line, startIdx ->
        prettyPrint(fileName, FontStyle.BOLD)
        prettyPrint(": ", FontStyle.BOLD)

        print(line.take(startIdx))
        prettyPrint(line.substring(startIdx, startIdx + text.length), FontStyle.BOLD, FontStyle.RED)
        println(line.drop(startIdx + text.length))
    }
}