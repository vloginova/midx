package com.vloginova.midx.util

import com.vloginova.midx.util.collections.TrigramSet
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

private const val defaultBufferSize = 8 * 1024

/**
 * Creates Trigram Set for a given [text]. Should not be used for large inputs.
 * Line separators CR, CRLF and LF are treated uniformly.
 */
fun createTrigramSet(text: String): TrigramSet {
    if (text.length < 3) return TrigramSet()
    val trigramSet = TrigramSet(calculateInitialTrigramSetSize(text.length.toLong()))
    addToTrigramSetTreatLineSeparators(text.toCharArray(), text.length, trigramSet)
    return trigramSet
}

/**
 * Creates Trigram Set for a given [file].
 * Line separators CR, CRLF and LF are treated uniformly.
 */
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

            addToTrigramSetTreatLineSeparators(buffer, accumulatedCharsNumber, trigramSet)

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

/**
 * Populates [trigramSet] with trigrams retrieved from [chars] so that all line separators (CR, CRLF and LF) are
 * treated in the same way.
 *
 * NOTE: Mutates [chars] array. But guaranteed that two last chars are correct and meaningful and can be used as a
 * start of the next chunk of input.
 */
private fun addToTrigramSetTreatLineSeparators(chars: CharArray, untilIndex: Int, trigramSet: TrigramSet) {
    check(untilIndex >= 3) { "Able to process only inputs of length 3 and greater" }

    var fromIndex = 0
    var lineSeparatorIndex = chars.indexOfLineSeparator(0, untilIndex)

    while (lineSeparatorIndex != -1) {
        val isCr = chars[lineSeparatorIndex] == '\r'
        // Treat all separator as LF
        chars[lineSeparatorIndex] = '\n'
        addToTrigramSet(chars, fromIndex, lineSeparatorIndex + 1, trigramSet)

        if (isCr && lineSeparatorIndex == untilIndex - 1) {
            // Preserve CR, because next chunk can start with \n, and it should be treated as one symbol
            chars[lineSeparatorIndex] = '\r'
            return
        }

        // Next iteration starts two symbols before next line start in order not to lose overlapping trigrams
        fromIndex = max(fromIndex, lineSeparatorIndex - 1)

        /*
        Skip LF for CRLF: move symbol before last line separator forward.
        Example:
        Step 1 (before method call):    abc\r\nabc
        Step 2 (at this point):         abc\n\nabc
        Step 3 (before next iteration): abcc\nabc
        So, input for the next iteration will be c\nabc
        */
        if (isCr && chars[lineSeparatorIndex + 1] == '\n') {
            if (fromIndex < lineSeparatorIndex) {
                chars[lineSeparatorIndex] = chars[lineSeparatorIndex - 1]
            }
            fromIndex++
            lineSeparatorIndex++
        }
        lineSeparatorIndex = chars.indexOfLineSeparator(lineSeparatorIndex + 1, untilIndex)
    }

    addToTrigramSet(chars, fromIndex, untilIndex, trigramSet)
}

private fun CharArray.indexOfLineSeparator(fromIndex: Int, untilIndex: Int): Int {
    for (i in fromIndex until untilIndex) {
        if (this[i] == '\n' || this[i] == '\r') {
            return i
        }
    }
    return -1
}

private fun addToTrigramSet(chars: CharArray, fromIndex: Int, untilIndex: Int, trigramSet: TrigramSet) {
    if (untilIndex - fromIndex < 3) return

    var unigram = chars[fromIndex + 2].toInt()
    var bigram = chars[fromIndex + 1].toInt() shl 8 or unigram
    var trigram = chars[fromIndex].toInt() shl 16 or bigram
    trigramSet.add(trigram)

    for (i in fromIndex + 3 until untilIndex) {
        val char = chars[i].toInt()
        trigram = bigram shl 8 or char
        bigram = unigram shl 8 or char
        unigram = char
        trigramSet.add(trigram)
    }
}