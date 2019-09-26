package com.vloginova.midx.util

import com.vloginova.midx.util.collections.TrigramSet
import java.io.File
import kotlin.math.sqrt

private const val TRIGRAM_LENGTH = 3
private const val MIN_BUFFER_SIZE = TRIGRAM_LENGTH * 2  // the worst case scenario: \r\n\r\n\r\n

private const val defaultBufferSize = 8 * 1024

/**
 * Creates Trigram Set for a given [text]. Should not be used for large inputs.
 * Treats line separators CR, CRLF and LF uniformly.
 */
internal fun createTrigramSet(text: String): TrigramSet {
    if (text.length < TRIGRAM_LENGTH) return TrigramSet()
    val trigramSet = TrigramSet(calculateInitialCapacity(text.length.toLong()))
    addToTrigramSet(text.toCharArray(), text.length, trigramSet)
    return trigramSet
}

/**
 * Creates Trigram Set for a given [file], buffering characters for efficient processing, [bufferSize] defines the size
 * of the buffer. To cancel processing [checkCancelled] should be used.
 * Treats line separators CR, CRLF and LF uniformly.
 *
 * @param checkCancelled Can be used to cancel the function. Should do necessary work to stop further processing, e.g,
 * throw some cancellation exception, suited for the context
 *
 * @exception java.io.IOException If an I/O error occurs
 */
internal fun createTrigramSet(
    file: File,
    checkCancelled: () -> Unit = { },
    bufferSize: Int = defaultBufferSize
): TrigramSet {
    require(bufferSize >= MIN_BUFFER_SIZE) { "Trigram buffer size cannot be less than $MIN_BUFFER_SIZE" }

    val trigramSet = TrigramSet(calculateInitialCapacity(file.length()))
    val buffer = CharArray(bufferSize)
    file.bufferedReader(Charsets.UTF_8).use {
        var accumulatedCharsNumber = 0
        while (true) {
            checkCancelled()

            val readCharsNumber = it.read(buffer, accumulatedCharsNumber, buffer.size - accumulatedCharsNumber)
            if (readCharsNumber < 0) break

            accumulatedCharsNumber += readCharsNumber

            addToTrigramSet(buffer, accumulatedCharsNumber, trigramSet)

            if (accumulatedCharsNumber >= MIN_BUFFER_SIZE - 2) {
                buffer.copyInto(
                    buffer, destinationOffset = 0,
                    startIndex = accumulatedCharsNumber - (MIN_BUFFER_SIZE - 2),
                    endIndex = accumulatedCharsNumber
                )
                accumulatedCharsNumber = MIN_BUFFER_SIZE - 2
            }
        }
    }
    return trigramSet
}

/**
 * Calculates initial size as a trade off between allocated space and number of subsequent TrigramSet backing array
 * resize count.
 * TODO: try to optimize for small inputs
 */
private fun calculateInitialCapacity(sourceSize: Long) = (sqrt(sourceSize.toDouble()) * 36).toInt()

/**
 * Populates [trigramSet] with trigrams retrieved from [chars] so that all the line separators (CR, CRLF and LF) are
 * treated in the same way.
 */
private fun addToTrigramSet(chars: CharArray, length: Int, trigramSet: TrigramSet) {
    if (length < TRIGRAM_LENGTH) return

    var offset = 0

    if (chars.isCrLfAt(offset, length)) offset++
    val firstChar = chars.properCharAt(offset++).toInt()

    if (chars.isCrLfAt(offset, length)) offset++
    val secondChar = chars.properCharAt(offset++).toInt()

    var unigram: Int = secondChar
    var bigram: Int = firstChar shl 8 or unigram
    var trigram: Int

    for (i in offset until length) {
        if (chars.isCrLfAt(i, length)) continue
        val char = chars.properCharAt(i).toInt()
        trigram = bigram shl 8 or char
        bigram = unigram shl 8 or char
        unigram = char
        trigramSet.add(trigram)
    }
}

private fun CharArray.isCrLfAt(offset: Int, length: Int = size): Boolean {
    return offset + 1 < length && this[offset] == '\r' && this[offset + 1] == '\n'
}

private fun CharArray.properCharAt(offset: Int): Char {
    val char = this[offset]
    return if (char == '\r') '\n' else char
}
