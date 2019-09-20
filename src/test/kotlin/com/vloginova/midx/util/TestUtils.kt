package com.vloginova.midx.util

import com.vloginova.midx.util.collections.IntSet
import com.vloginova.midx.util.collections.intHashCode
import kotlin.random.Random

fun IntSet.toSet() : Set<Int>  {
    val set = HashSet<Int>()
    for (value in this) {
        set.add(value)
    }
    return set
}

fun getRandomInt(randomValuesNumber: Int): Int {
    return Random.nextInt(
        -randomValuesNumber / 2,
        randomValuesNumber / 2 + randomValuesNumber % 2
    )
}

fun generateIntWithHashcodeFor(arrayCapacity: Int, expectedIndex: Int): Sequence<Int> {
    return sequence {
        var i = 0

        while (true) {
            val hashCode = intHashCode(i)
            if ((hashCode and (arrayCapacity - 1)) == expectedIndex)
                yield(i)
            i++
        }
    }
}