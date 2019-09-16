package com.vloginova.midx.util

fun String.toTrigramSet(): Set<Int> {
    if (length < 3) return emptySet()

    val tokens = HashSet<Int>()
    var trigram = (this[0].toInt() shl 16) or (this[1].toInt() shl 8) or this[2].toInt()
    var bigram = (this[1].toInt() shl 8) or this[2].toInt()
    var unigram = this[2].toInt()
    tokens.add(trigram)

    for (i in 3 until length) {
        trigram = bigram shl 8 or this[i].toInt()
        bigram = unigram shl 8 or this[i].toInt()
        unigram = this[i].toInt()
        tokens.add(trigram)
    }
    return tokens
}