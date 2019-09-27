package com.vloginova.midx

import com.vloginova.midx.util.collections.IntSet
import com.vloginova.midx.util.collections.intHashCode
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import kotlin.random.Random

val alphabet = (('A'..'z').toList() + ('А'..'я') + arrayOf('{', '}', '(', ')', '\n', '\r')).toCharArray()

fun IntSet.toSet(): Set<Int> {
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

fun createTempFileWithText(text: String): File {
    val tempFile = createTempFile()
    tempFile.deleteOnExit()
    tempFile.writeText(text)
    return tempFile
}

fun generateRandomText(length: Int): String {
    return (1..length)
        .map { Random.nextInt(0, alphabet.size) }
        .map(alphabet::get)
        .joinToString("")
}

fun <T> assertCollectionEquals(expected: Collection<T>, actual: Collection<T>) {
    assertEquals(expected.size, actual.size, "Size of actual collection differs from expected one")
    assertEquals(expected.size, actual.intersect(expected).size, "Collections elements don't match")
}