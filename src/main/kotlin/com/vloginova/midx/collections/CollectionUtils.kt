package com.vloginova.midx.collections

/**
 * Hash function is taken from here:
 * https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
 * It gives a pretty good distribution and respectively low number of collisions. Compared to Intellij Idea version,
 * it has approximately 4-5 times fewer collisions without initial capacity optimization, and it gives less
 * degradation when increasing load factor.
 */
internal fun intHashCode(y: Int): Int {
    val x = ((y shr 16) xor y) * 0x45d9f3b
    /* Found no difference in terms collisions count after commenting this */
//        x = ((x shr 16) xor x) * 0x45d9f3b
    return (x shr 16) xor x
}