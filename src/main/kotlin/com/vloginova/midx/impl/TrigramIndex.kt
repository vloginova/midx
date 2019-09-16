package com.vloginova.midx.impl

import com.vloginova.midx.api.Index
import com.vloginova.midx.util.forEachFile
import com.vloginova.midx.util.toTrigramSet
import java.io.File
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * The simple not thread safe implementation of index, keys are implemented as trigrams
 */
class TrigramIndex : Index {
    private val storage = HashMap<Int, MutableList<String>>()
    private var file: File? = null
    private var indexBuilt = false

    override fun build(file: File) {
        this.file = file
        file.forEachFile {
            it.readText(Charsets.UTF_8).toTrigramSet().forEach { word ->
                storage.putIfAbsent(word, ArrayList())
                storage[word]!!.add(it.path)
            }
        }
        indexBuilt = true

    }

    override fun search(text: String, processMatch: (String, String, Int) -> Unit) {
        if (text.isEmpty()) return

        if (!indexBuilt) throw IllegalStateException("Cannot search whe index is not yet built")

        if (text.length < 3) {
            file?.forEachFile { file ->
                file.fullTextSearch(text, processMatch)
            }
            return
        }

        matchingFileCandidates(text).forEach { filePath ->
            File(filePath).fullTextSearch(text, processMatch)
        }
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

    override fun cancelBuild() {
        TODO("not implemented")
    }

    private fun matchingFileCandidates(text: String): Collection<String> {
        val tokens = text.toTrigramSet()
        if (tokens.isEmpty()) return emptyList()

        var intersection = storage[tokens.first()]?.toSet() ?: emptySet()
        tokens.forEach { token ->
            storage[token]?.let { intersection = intersection.intersect(it) }
        }
        return intersection
    }

}
