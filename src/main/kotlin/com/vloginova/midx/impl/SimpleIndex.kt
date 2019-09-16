package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.util.forEachFile
import java.io.File
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * The simple implementation of index, without concurrency and any optimization
 * Not thread safe
 */
class SimpleIndex : Index {
    private val storage = HashMap<String, MutableList<String>>()
    private var indexBuilt = false

    override fun build(file: File) {
        file.forEachFile {
            tokenize(it.readText(Charsets.UTF_8)).forEach { word ->
                storage.putIfAbsent(word, ArrayList())
                storage[word]!!.add(it.path)
            }
        }
        indexBuilt = true
    }

    override fun search(text: String, processMatch: (String, String, Int) -> Unit) {
        if (text.isEmpty()) return

        if (!indexBuilt) throw IllegalStateException("Cannot search whe index is not yet built")

        matchingFileCandidates(text).forEach { filePath ->
            File(filePath).forEachLine(Charsets.UTF_8) { line ->
                var textPosition = line.indexOf(text)
                while (textPosition != -1) {
                    // TODO: exception handling
                    processMatch(filePath, line, textPosition)
                    textPosition = line.indexOf(text, textPosition + 1)
                }
            }
        }
    }

    override fun cancelBuild() {
        TODO("not implemented")
    }

    private fun matchingFileCandidates(text: String): Collection<String> {
        val tokens = tokenize(text)
        var intersection = emptySet<String>()

        tokens.forEachIndexed { i, word ->
            when (i) {
                0 -> {
                    val filesWithWord = HashSet<String>()
                    storage.keys
                        .filter { tokens.size != 1 && it.endsWith(word) || tokens.size == 1 && it.contains(word) }
                        .forEach {
                            filesWithWord.addAll(storage[it]!!)
                        }
                    intersection = filesWithWord
                }
                tokens.size - 1 -> {
                    val filesWithWord = HashSet<String>()
                    storage.keys.filter { it.startsWith(word) }.forEach {
                        filesWithWord.addAll(storage[it]!!)
                    }
                    intersection = intersection.intersect(filesWithWord)
                }
                else -> storage[word]?.let { intersection = intersection.intersect(it) }
            }
            if (intersection.isEmpty()) return@forEachIndexed

        }
        return intersection
    }

    private fun tokenize(text: String): List<String> {
        // TODO: add more delimiters
        return text.split(" ").map { word -> word.trim() }.filter { it.isNotBlank() }
    }

}
