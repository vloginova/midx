package com.vloginova.midx.util

import com.vloginova.midx.util.collections.TrigramSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

private const val defaultBufferSize = 8 * 1024

/**
 * Creates Trigram Set for a given [text]. Should not be used for large inputs.
 */
fun createTrigramSet(text: String): TrigramSet {
    val trigramSet = TrigramSet(calculateInitialTrigramSetSize(text.length.toLong()))
    addToTrigramSet(text.toCharArray(), text.length, trigramSet)
    return trigramSet
}

fun createTrigramSet(file: File, bufferSize: Int = defaultBufferSize): TrigramSet {
    require(bufferSize >= 3) { "Trigram buffer size cannot be less than 3" }

    val trigramSet = TrigramSet(calculateInitialTrigramSetSize(file.length()))
    val buffer = CharArray(bufferSize)
    file.bufferedReader(Charsets.UTF_8).use {
        var accumulatedCharsNumber = 0
        while (true) {
            val readCharsNumber = it.read(buffer, accumulatedCharsNumber, buffer.size - accumulatedCharsNumber)
            if (readCharsNumber < 0) break

            accumulatedCharsNumber += readCharsNumber
            if (accumulatedCharsNumber < 3) continue

            addToTrigramSet(buffer, accumulatedCharsNumber, trigramSet)

            buffer[0] = buffer[accumulatedCharsNumber - 2]
            buffer[1] = buffer[accumulatedCharsNumber - 1]
            accumulatedCharsNumber = 2
        }
    }
    return trigramSet
}

/**
 * Initial size is calculated as a trade off between allocated space and number of subsequent TrigramSet backing array
 * resize count.
 * TODO: try to optimize for small inputs
 */
private fun calculateInitialTrigramSetSize(sourceSize: Long) = (sqrt(sourceSize.toDouble()) * 36).toInt()

private fun addToTrigramSet(chars: CharArray, length: Int, trigramSet: TrigramSet) {
    if (length < 3) return

    var unigram = chars[2].toInt()
    var bigram = chars[1].toInt() shl 8 or unigram
    var trigram = chars[0].toInt() shl 16 or bigram
    trigramSet.add(trigram)

    for (i in 3 until length) {
        val char = chars[i].toInt()
        trigram = bigram shl 8 or char
        bigram = unigram shl 8 or char
        unigram = char
        trigramSet.add(trigram)
    }
}