package com.vloginova.midx.util

import com.vloginova.midx.util.collections.TrigramSet

fun String.toTrigramSet(): TrigramSet {
    if (length < 3) return TrigramSet(0)

    val trigrams = TrigramSet()
    var trigram = (this[0].toInt() shl 16) or (this[1].toInt() shl 8) or this[2].toInt()
    var bigram = (this[1].toInt() shl 8) or this[2].toInt()
    var unigram = this[2].toInt()
    trigrams.add(trigram)

    for (i in 3 until length) {
        trigram = bigram shl 8 or this[i].toInt()
        bigram = unigram shl 8 or this[i].toInt()
        unigram = this[i].toInt()
        trigrams.add(trigram)
    }
    return trigrams
}